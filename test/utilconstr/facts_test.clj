(ns utilconstr.facts-test
  (:require [clojure.test :refer [deftest is]]
            [utilconstr.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:utility-locate-provenance (facts/spec-basis "JPN"))))
  (is (= :quantitative (:threshold-model (facts/spec-basis "JPN"))))
  (is (= 168 (:notification-lead-hours (facts/spec-basis "JPN")))))

(deftest usa-has-a-spec-basis-with-a-different-numeric-lead-time
  (is (= :quantitative (:threshold-model (facts/spec-basis "USA"))))
  (is (= 24 (:notification-lead-hours (facts/spec-basis "USA")))))

(deftest deu-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU"))))
  (is (nil? (:notification-lead-hours (facts/spec-basis "DEU")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

;; ----------------------------- notification-lead-insufficient? -----------------------------

(deftest jpn-lead-time-is-a-real-numeric-recheck
  (is (true? (facts/notification-lead-insufficient? "JPN" {:notification-lead-hours-actual 20})))
  (is (false? (facts/notification-lead-insufficient? "JPN" {:notification-lead-hours-actual 168})))
  (is (false? (facts/notification-lead-insufficient? "JPN" {:notification-lead-hours-actual 200}))))

(deftest usa-lead-time-uses-its-own-different-numeric-minimum
  (is (true? (facts/notification-lead-insufficient? "USA" {:notification-lead-hours-actual 10})))
  (is (false? (facts/notification-lead-insufficient? "USA" {:notification-lead-hours-actual 24}))))

(deftest deu-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" {:notification-lead-hours-actual 1000})))
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" {:notification-lead-hours-actual 0}))))

(deftest unknown-jurisdiction-returns-nil-not-a-guess
  (is (nil? (facts/notification-lead-insufficient? "ATL" {:notification-lead-hours-actual 1000}))))

(deftest non-numeric-actual-never-fires-a-quantitative-hold
  (is (false? (facts/notification-lead-insufficient? "JPN" {:notification-lead-hours-actual nil}))))

;; ----------------------------- catalog citation honesty -----------------------------

(deftest jpn-cites-real-utility-locate-and-traffic-control-law
  (let [sb (facts/spec-basis "JPN")]
    (is (re-find #"労働安全衛生規則" (:utility-locate-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:utility-locate-provenance sb)))
    (is (re-find #"道路交通法|道路使用許可" (:traffic-control-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:traffic-control-provenance sb)))))

(deftest usa-cites-real-osha-and-fhwa-law
  (let [sb (facts/spec-basis "USA")]
    (is (re-find #"1926\.651" (:utility-locate-basis sb)))
    (is (re-find #"osha\.gov" (:utility-locate-provenance sb)))
    (is (re-find #"630" (:traffic-control-basis sb)))
    (is (re-find #"MUTCD" (:traffic-control-basis sb)))
    (is (re-find #"ecfr\.gov" (:traffic-control-provenance sb)))
    (is (re-find #"645\.213" (:permit-basis sb)))
    (is (re-find #"ecfr\.gov" (:permit-provenance sb)))
    (is (re-find #"state DOT" (:permit-note sb)) "honestly labeled state/local layering for non-Federal-aid roads")))

(deftest deu-cites-real-eu-directive-and-german-stvo
  (let [sb (facts/spec-basis "DEU")]
    (is (re-find #"92/57" (:utility-locate-basis sb)))
    (is (re-find #"eur-lex\.europa\.eu" (:utility-locate-provenance sb)))
    (is (re-find #"StVO|Straßenverkehrs-Ordnung" (:traffic-control-basis sb)))
    (is (re-find #"gesetze-im-internet\.de" (:traffic-control-provenance sb)))))

(deftest uncovered-jurisdiction-has-no-fabricated-catalog-entry
  (is (nil? (facts/spec-basis "ATL"))))
