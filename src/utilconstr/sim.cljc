(ns utilconstr.sim
  "Demo driver -- `clojure -M:dev:run`. Walks utility-line construction
  sites through a full coordination episode, exercising every governor
  check: log a site record (auto-commits) -> propose a construction-
  operation schedule (escalates, human approves) -> flag a safety concern
  (ALWAYS escalates, human approves, safety-concern notice actually
  'sent' via the mock notifier) -> log the concern's resolution (auto-
  commits) -> re-propose the schedule now that the concern is resolved
  (escalates, approved) -> order supplies below the cost threshold
  (AUTO-COMMITS) -> order supplies above the cost threshold (escalates,
  approved) -> then SIX HARD holds that never reach a human at all: an
  uncovered jurisdiction, a not-independently-verified site, an
  incomplete utility locate, an insufficient notification lead time, an
  unresolved safety concern, and an op outside the closed four-op
  allowlist -- then a cross-jurisdiction (USA, quantitative) and a
  qualitative (DEU/EU, never fabricates a numeric lead-time) schedule
  walkthrough. Finally prints the audit ledger + every coordination-
  artifact history."
  (:require [langgraph.graph :as g]
            [utilconstr.store :as store]
            [utilconstr.notify :as notify]
            [utilconstr.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :site-supervisor :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        notifier (notify/mock-notifier)
        actor (op/build db {:notifier notifier})]
    (println "== log-site-record site-1 (trenching-progress update, no ground-truth booleans touched) (AUTO-COMMITS) ==")
    (println (exec! actor "t1" {:op :log-site-record :subject "site-1"
                                :patch {:id "site-1" :trenching-percent-complete 40}} operator))

    (println "== schedule-construction-operation site-1 (clean; escalates -- human approves) ==")
    (let [r (exec! actor "t2" {:op :schedule-construction-operation :subject "site-1"
                               :window {:proposed-start-date "2026-08-01" :proposed-end-date "2026-08-15"}
                               :notes "本管布設先行、その後宅内引込工事"} operator)]
      (println r)
      (println (approve! actor "t2")))

    (println "== flag-safety-concern site-1 (ALWAYS escalates -- human approves; safety-concern notice actually sent via mock notifier) ==")
    (let [r (exec! actor "t3" {:op :flag-safety-concern :subject "site-1"
                               :concern-type :utility-strike
                               :concern-description "試掘溝で未表示のガス管を確認、埋設物損傷リスクの可能性。"} operator)]
      (println r)
      (println (approve! actor "t3")))
    (println "-- sent log --")
    (println (notify/sent-log notifier))

    (println "== log-site-record site-1: concern resolved after inspection (AUTO-COMMITS) ==")
    (println (exec! actor "t4" {:op :log-site-record :subject "site-1"
                                :patch {:id "site-1" :safety-concern-unresolved? false}} operator))

    (println "== schedule-construction-operation site-1 AGAIN, now that the concern is resolved (escalates -- human approves) ==")
    (let [r (exec! actor "t5" {:op :schedule-construction-operation :subject "site-1"
                               :window {:proposed-start-date "2026-08-08" :proposed-end-date "2026-08-22"}
                               :notes "埋設物防護措置を反映した改訂スケジュール"} operator)]
      (println r)
      (println (approve! actor "t5")))

    (println "== order-supplies site-1, cost below threshold (AUTO-COMMITS at phase 3) ==")
    (println (exec! actor "t6" {:op :order-supplies :subject "site-1"
                                :items ["HDPE-pipe-150mm-100m" "trench-shoring-panels-10"]
                                :cost-usd 1200 :vendor "Local Utility Supply Co."} operator))

    (println "== order-supplies site-1, cost ABOVE threshold (escalates -- human approves) ==")
    (let [r (exec! actor "t7" {:op :order-supplies :subject "site-1"
                               :items ["directional-boring-rig-rental"] :cost-usd 18000 :vendor "Heavy Equip Rentals"} operator)]
      (println r)
      (println (approve! actor "t7")))

    (println "== schedule-construction-operation site-2 (ATL, no spec-basis -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :schedule-construction-operation :subject "site-2" :window {}} operator))

    (println "== schedule-construction-operation site-3 (site-verified? false -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :schedule-construction-operation :subject "site-3" :window {}} operator))

    (println "== schedule-construction-operation site-4 (utility-locate-completed? false -> HARD hold) ==")
    (println (exec! actor "t10" {:op :schedule-construction-operation :subject "site-4" :window {}} operator))

    (println "== schedule-construction-operation site-5 (JPN, notification-lead-hours-actual=20 < 168-hour legal minimum -> HARD hold) ==")
    (println (exec! actor "t11" {:op :schedule-construction-operation :subject "site-5" :window {}} operator))

    (println "== schedule-construction-operation site-6 (safety-concern-unresolved? true on file -> HARD hold) ==")
    (println (exec! actor "t12" {:op :schedule-construction-operation :subject "site-6" :window {}} operator))

    (println "== :direct-equipment-command site-1 (outside the closed 4-op allowlist -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t13" {:op :direct-equipment-command :subject "site-1"} operator))

    (println "== USA (quantitative, 24-hour utility-locate lead floor) -- schedule-construction-operation site-7, lead=30h, sufficient (escalates -- human approves) ==")
    (let [r (exec! actor "t14" {:op :schedule-construction-operation :subject "site-7"
                                :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-20"}} operator)]
      (println r)
      (println (approve! actor "t14")))

    (println "== DEU/EU (qualitative -- no fixed numeric lead-time, never fabricated) -- schedule-construction-operation site-8 (escalates -- human approves) ==")
    (let [r (exec! actor "t15" {:op :schedule-construction-operation :subject "site-8"
                                :window {:proposed-start-date "2026-09-10" :proposed-end-date "2026-09-25"}} operator)]
      (println r)
      (println (approve! actor "t15")))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== site-record-log ==")
    (doseq [r (store/site-record-log-history db)] (println r))

    (println "== schedule-proposal history ==")
    (doseq [r (store/schedule-proposal-history db)] (println r))

    (println "== safety-concern-flag history ==")
    (doseq [r (store/safety-concern-flag-history db)] (println (get r "document")))

    (println "== supply-order-proposal history ==")
    (doseq [r (store/supply-order-proposal-history db)] (println r))))
