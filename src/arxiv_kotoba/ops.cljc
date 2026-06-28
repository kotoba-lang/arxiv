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
  {:primary "cs.DB"
   :cross-lists ["cs.DC" "cs.CR"]
   :rationale
   "Kotoba's core contribution is a content-addressed Datalog/datom database substrate; distributed systems and security are secondary contributions."})

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
   :advice recommended-categories})

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
           category-ok? (and (= "cs.DB" primary)
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

      plan)))
