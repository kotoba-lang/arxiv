(ns arxiv-kotoba.manifest)

(def actor
  {:actor/id :actor/arxiv
   :actor/kind :organism
   :actor/domain :scholarly-publishing
   :actor/name "arXiv yorishiro organism"
   :actor/represents {:external/service "arxiv.org"
                      :not-official true}
   :actor/capabilities
   [{:op :arxiv/search
     :risk :read-only
     :skill :arxiv.search}
    {:op :arxiv/advise-categories
     :risk :read-only
     :skill :arxiv.advise-categories}
    {:op :arxiv/validate-package
     :risk :read-only
     :skill :arxiv.validate-package}
    {:op :arxiv/login
     :risk :external-auth
     :skill :arxiv.login}
    {:op :arxiv/create-submission
     :risk :external-draft
     :skill :arxiv.create-submission}
    {:op :arxiv/upload-source
     :risk :external-draft
     :skill :arxiv.upload-source}
    {:op :arxiv/final-submit
     :risk :public-submit
     :skill :arxiv.final-submit
     :requires [:human-approval]}
    {:op :arxiv/check-status
     :risk :read-only
     :skill :arxiv.check-status}]
   :actor/yorishiro :yorishiro/arxiv})

(def yorishiro
  {:yorishiro/id :yorishiro/arxiv
   :yorishiro/surfaces
   [{:route/id :api
     :route/kind :api
     :route/capabilities [:arxiv/search]
     :route/prefer 10}
    {:route/id :browser-dom
     :route/kind :browser
     :route/capabilities [:arxiv/login
                          :arxiv/create-submission
                          :arxiv/upload-source
                          :arxiv/final-submit
                          :arxiv/check-status]
     :route/prefer 50}
    {:route/id :computer
     :route/kind :computer
     :route/capabilities [:arxiv/login
                          :arxiv/create-submission
                          :arxiv/upload-source
                          :arxiv/check-status]
     :route/prefer 80}
    {:route/id :human-handoff
     :route/kind :human
     :route/capabilities [:arxiv/final-submit]
     :route/prefer 100}]})

(defn capability
  [op]
  (first (filter #(= op (:op %)) (:actor/capabilities actor))))

