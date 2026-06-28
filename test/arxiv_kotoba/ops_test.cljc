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

(deftest dialogue-does-not-execute
  (let [r (dialogue/respond {:text "Can you submit this paper?"})]
    (is (= :ok (:status r)))
    (is (re-find #"requires human approval" (:text r)))))

