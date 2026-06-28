(ns arxiv-kotoba.ops
  (:require [arxiv-kotoba.manifest :as manifest]
            [arxiv-kotoba.policy :as policy]
            [arxiv-kotoba.routes :as routes]))

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
  (invoke-plan request ctx))

