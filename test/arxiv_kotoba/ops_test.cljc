(ns arxiv-kotoba.ops-test
  (:require [clojure.test :refer [deftest is]]
            [arxiv-kotoba.dialogue :as dialogue]
            [arxiv-kotoba.ops :as ops]))

(deftest read-only-op-routes-without-approval
  (is (= {:status :ready
          :actor/id :actor/arxiv
          :op :arxiv/search
          :skill :arxiv.search
          :risk :read-only
          :route/id :api
          :route/kind :api
          :params {:q "Wheeler-DeWitt"}}
         (ops/invoke {:op :arxiv/search :q "Wheeler-DeWitt"}
                     {:available-surfaces #{:api}}))))

(deftest final-submit-holds-without-approval
  (let [r (ops/invoke {:op :arxiv/final-submit :submission-id "1234"}
                      {:available-surfaces #{:browser}})]
    (is (= :hold (:status r)))
    (is (= :approval-required (:reason r)))
    (is (= :public-submit (:risk r)))))

(deftest final-submit-routes-after-approval
  (let [r (ops/invoke {:op :arxiv/final-submit :submission-id "1234"}
                      {:available-surfaces #{:browser}
                       :approved? true})]
    (is (= :ready (:status r)))
    (is (= :browser-dom (:route/id r)))
    (is (= :arxiv.final-submit (:skill r)))))

(deftest no-route-is-explicit
  (is (= {:status :error
          :error :no-route
          :op :arxiv/search}
         (ops/invoke {:op :arxiv/search} {:available-surfaces #{:computer}}))))

(deftest local-category-advice-is-ready
  (let [r (ops/invoke {:op :arxiv/advise-categories}
                      {:available-surfaces #{:local}})]
    (is (= :ready (:status r)))
    (is (= :local (:route/id r)))
    (is (= "cs.DB" (get-in r [:result :advice :primary])))
    (is (= ["cs.DC" "cs.CR"] (get-in r [:result :advice :cross-lists])))))

(deftest kotoba-package-validates
  (let [r (ops/invoke {:op :arxiv/validate-package
                       :package-edn "submissions/kotoba/package.edn"}
                      {:available-surfaces #{:local}})]
    (is (= :ready (:status r)))
    (is (= :ok (get-in r [:result :status])))
    (is (= [] (get-in r [:result :errors])))
    (is (= "cs.DB" (get-in r [:result :categories :primary])))))

(deftest dialogue-does-not-execute
  (let [r (dialogue/respond {:text "Can you submit this paper?"})]
    (is (= :ok (:status r)))
    (is (re-find #"requires human approval" (:text r)))))
