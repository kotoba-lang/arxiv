(ns arxiv-kotoba.ops
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [arxiv-kotoba.manifest :as manifest]
            [arxiv-kotoba.policy :as policy]
            [arxiv-kotoba.routes :as routes]
            #?(:clj [clojure.java.io :as io])))

(def required-source-files
  #{"kotoba.tex" "references.bib"})

(def recommended-categories
  {:primary "cs.CL"
   :cross-lists ["cs.DB" "cs.DC" "cs.CR"]
   :rationale
   "For arXiv submission, frame Kotoba as accountable memory infrastructure for language agents; the Datalog/datom database, distributed substrate, and security architecture are supporting technical contributions."})

(def endorsement-fallbacks
  [{:when {:server-message "You are not endorsed for this archive."}
    :hold-reason :endorsement-required
    :next-action :choose-endorsed-category-or-request-endorsement
    :notes ["Record the attempted category and arXiv server message."
            "Retry with the nearest endorsed primary category when the framing remains truthful."
            "Do not submit to an unrelated archive solely to bypass endorsement."]}])

#?(:clj
   (defn- read-edn-file [path]
     (edn/read-string (slurp path))))

#?(:clj
   (defn- exists? [path]
     (.exists (io/file path))))

#?(:clj
   (defn- list-file-names [dir]
     (->> (.listFiles (io/file dir))
          (filter #(.isFile %))
          (map #(.getName %))
          set)))

(defn advise-categories
  [_request]
  {:status :ok
   :advice recommended-categories
   :endorsement-fallbacks endorsement-fallbacks})

#?(:clj
   (defn validate-package
     [{:keys [package-edn]}]
     (let [package-path (or package-edn "submissions/kotoba/package.edn")
           package (read-edn-file package-path)
           source-dir (:submission/source-dir package)
           source-files (when (exists? source-dir) (list-file-names source-dir))
           missing-source (if source-files
                            (sort (remove source-files required-source-files))
                            (sort required-source-files))
           primary (:submission/primary-category package)
           cross-lists (set (:submission/cross-lists package))
           category-ok? (and (= "cs.CL" primary)
                             (contains? cross-lists "cs.DB")
                             (contains? cross-lists "cs.DC")
                             (contains? cross-lists "cs.CR"))
           tex-path (str source-dir "/kotoba.tex")
           tex (when (exists? tex-path) (slurp tex-path))
           title-ok? (boolean (and tex
                                    (str/includes?
                                     tex
                                     "A Content-Addressed Datalog Substrate")))
           abstract-ok? (boolean (and tex
                                       (str/includes? tex "\\begin{abstract}")
                                       (str/includes? tex "\\end{abstract}")))
           final-gated? (= :human-approval
                           (:submission/final-submit-requires package))
           errors (cond-> []
                    (seq missing-source)
                    (conj {:error :missing-source-files
                           :files missing-source})
                    (not category-ok?)
                    (conj {:error :category-mismatch
                           :expected recommended-categories
                           :actual {:primary primary
                                    :cross-lists (:submission/cross-lists package)}})
                    (not title-ok?)
                    (conj {:error :title-not-found-in-tex})
                    (not abstract-ok?)
                    (conj {:error :abstract-not-found-in-tex})
                    (not final-gated?)
                    (conj {:error :final-submit-not-human-gated}))]
       {:status (if (seq errors) :error :ok)
        :package package-path
        :source-dir source-dir
        :required-source-files (sort required-source-files)
        :categories {:primary primary
                     :cross-lists (:submission/cross-lists package)}
        :errors errors}))
   :cljs
   (defn validate-package
     [_request]
     {:status :error
      :error :jvm-required}))

(defn- submission-state
  [status]
  (or (:submission/state status) :draft))

(defn- endorsement-hold?
  [status]
  (and (= :on-hold (submission-state status))
       (= :endorsement-required (:submission/hold-reason status))))

#?(:clj
   (defn plan-submission
     [{:keys [package-edn status-edn]}]
     (let [package-path (or package-edn "submissions/kotoba/package.edn")
           status-path (or status-edn "submissions/kotoba/status.edn")
           package (read-edn-file package-path)
           status (when (exists? status-path) (read-edn-file status-path))
           validation (validate-package {:package-edn package-path})
           state (submission-state status)
           hold? (endorsement-hold? status)
           steps [{:id :validate-package
                   :op :arxiv/validate-package
                   :state (if (= :ok (:status validation)) :complete :blocked)}
                  {:id :start-submission
                   :op :arxiv/create-submission
                   :category (:submission/primary-category package)
                   :state (cond
                            hold? :blocked
                            (= :draft state) :next
                            :else :complete)}
                  {:id :upload-source
                   :op :arxiv/upload-source
                   :source-archive (:submission/source-archive package)
                   :state (cond
                            (not= :ok (:status validation)) :blocked
                            hold? :blocked
                            (#{:uploaded :pending-human-final-submit :submitted} state) :complete
                            :else :pending)}
                  {:id :final-submit
                   :op :arxiv/final-submit
                   :state (cond
                            (= :submitted state) :complete
                            (= :pending-human-final-submit state) :awaiting-human-approval
                            :else :pending)
                   :requires [:human-approval]}]]
       {:status (cond
                  (not= :ok (:status validation)) :error
                  hold? :hold
                  :else :ready)
        :submission/id (:submission/id package)
        :package package-path
        :state state
        :categories {:primary (:submission/primary-category package)
                     :cross-lists (:submission/cross-lists package)}
        :next-action (cond
                       (not= :ok (:status validation)) :fix-package
                       hold? (:submission/next-action status)
                       (= :pending-human-final-submit state) :human-final-submit-approval
                       (= :submitted state) :none
                       :else :continue-draft-workflow)
        :hold (when hold?
                {:reason (:submission/hold-reason status)
                 :attempted-category (:submission/attempted-primary-category status)
                 :server-message (:submission/server-message status)
                 :fallbacks endorsement-fallbacks})
        :steps steps
        :validation validation}))
   :cljs
   (defn plan-submission
     [_request]
     {:status :error
      :error :jvm-required}))

(defn invoke-plan
  "Pure capability dispatcher. It does not touch arXiv; it returns the route and
  skill that a host runner should execute, or a hold/error map."
  [{:keys [op] :as request} ctx]
  (if-let [cap (manifest/capability op)]
    (if-not (policy/allowed? ctx cap)
      {:status :hold
       :reason :approval-required
       :op op
       :risk (:risk cap)
       :requires (:requires cap)}
      (if-let [route (routes/choose-route manifest/yorishiro op ctx)]
        {:status :ready
         :actor/id (:actor/id manifest/actor)
         :op op
         :skill (:skill cap)
         :risk (:risk cap)
         :route/id (:route/id route)
         :route/kind (:route/kind route)
         :params (dissoc request :op)}
        {:status :error
         :error :no-route
         :op op}))
    {:status :error
     :error :unknown-op
     :op op}))

(defn invoke
  [request ctx]
  (let [plan (invoke-plan request ctx)]
    (case (:op request)
      :arxiv/advise-categories
      (if (= :ready (:status plan))
        (assoc plan :result (advise-categories request))
        plan)

      :arxiv/validate-package
      (if (= :ready (:status plan))
        (assoc plan :result (validate-package (dissoc request :op)))
        plan)

      :arxiv/plan-submission
      (if (= :ready (:status plan))
        (assoc plan :result (plan-submission (dissoc request :op)))
        plan)

      plan)))
