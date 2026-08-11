(ns utilconstr.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  This namespace drives the REAL actor stack -- `utilconstr.store/seed-db`
  -> `utilconstr.operation/build` -> langgraph `g/run*` -> `utilconstr.
  advisor` -> `utilconstr.governor` -> `utilconstr.phase` -- and renders
  what actually came back. It contains NO governor logic of its own: every
  disposition, every HARD-hold rule and every rule detail string on the
  page is read out of the audit ledger / run state the actor produced.

  ## Input provenance

  Every subject id fed to the actor (`site-1`..`site-8`) is a literal key
  of `utilconstr.store/demo-data`'s `:sites` map -- verified against the
  seed before this file was written. No id is invented. Every column
  rendered in the site table is a field of that same seed model (and of
  `utilconstr.store`'s `site-spec`): `:id` `:name` `:jurisdiction`
  `:site-verified?` `:utility-locate-completed?` `:notification-lead-
  hours-actual` `:safety-concern-unresolved?` `:trenching-percent-
  complete` `:status`. Nothing is copied from a sibling actor's domain.

  ## The one constructed fixture, and why

  Governor checks 2 (`:effect-not-propose`) and 3 (`:forbidden-action-
  class`) exist precisely because this actor's OWN mock advisor can never
  trip them -- `utilconstr.governor`'s ns docstring says so: 'even though
  this actor's own mock advisor never sets these, the governor checks
  independently so a compromised advisor gains nothing by trying'. To
  show that defence actually holding rather than merely asserting it, the
  last run swaps in `compromised-advisor` (below) -- an Advisor stub that
  returns `:effect :actuate` with `:equipment-control?`/`:direct-
  actuation?` markers. It is injected through the actor's own documented
  `:advisor` seam, runs against the same seeded store and the same real
  governor, and targets the real seeded site `site-1`. Nothing about the
  governor is stubbed.

  ## Determinism

  No timestamps, no random, no wall-clock, no environment lookup reaches
  the page: the store is a freshly seeded `MemStore`, the advisor is the
  deterministic mock, ledger order follows call order, and every map
  iterated for output is explicitly sorted. Two runs are byte-identical
  (verify by rendering to two paths and diffing).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [utilconstr.advisor :as advisor]
            [utilconstr.facts :as facts]
            [utilconstr.notify :as notify]
            [utilconstr.operation :as op]
            [utilconstr.store :as store]))

(def ^:private operator
  "The same operator context this repo's own `utilconstr.sim` demo driver
  uses: a site supervisor running the actor at phase 3."
  {:actor-id "op-1" :actor-role :site-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(def ^:private compromised-advisor
  "A deliberately malfunctioning Advisor -- see ns docstring. It is NOT a
  governor stub and NOT a fake actor: it is injected through
  `utilconstr.operation/build`'s documented `:advisor` seam so the REAL
  `utilconstr.governor` gets to reject it. Structural checks 2 and 3 are
  unreachable from the honest mock advisor, so this is the only way to
  show them holding."
  (reify advisor/Advisor
    (-advise [_ _store request]
      {:summary    (str (:subject request) ": 掘削機の直接操作 + タイイン確定を含む発注提案")
       :rationale  "（誤動作/侵害された助言者の模擬 -- ガバナーが独立に拒否できることの実証）"
       :cites      [(:subject request)]
       :effect     :actuate
       :value      {:site-id (:subject request)
                    :items ["excavator-direct-control-link"]
                    :cost-usd 900
                    :vendor "Rogue Equipment Control Ltd."
                    :equipment-control? true
                    :direct-actuation? true
                    :finalizes-tie-in-authorization? true}
       :stake      :order-supplies
       :confidence 0.99})))

;; ----------------------------- the real run -----------------------------

(defn run-demo!
  "Runs a freshly seeded store through a scenario that reaches every
  disposition and ALL EIGHT `utilconstr.governor` HARD checks, using only
  site ids that exist in `utilconstr.store/demo-data`.

  Clean lifecycle on `site-1` (JPN, verified, locate complete, 200h lead,
  no open concern): a trenching-progress `:log-site-record` (auto-commits
  at phase 3), a construction-operation schedule proposal (ALWAYS
  escalates -- never in any phase's `:auto` set -- approved), a
  safety-concern flag (ALWAYS escalates, approved, notice actually
  dispatched through the injected notifier, and the site's own
  `:safety-concern-unresolved?` flips true), a second `:log-site-record`
  recording the concern as resolved (auto-commits), a re-proposed
  schedule now that it is resolved (escalates, approved), a supply order
  BELOW the 5000 USD threshold (auto-commits) and one ABOVE it
  (escalates, approved). Then cross-jurisdiction schedules: `site-7`
  (USA, quantitative, 30h vs the 24h floor -- sufficient) and `site-8`
  (DEU, honestly `:qualitative`, no fabricated numeric lead-time).

  Then eight HARD holds, none of which ever reaches a human:
  `site-2` (ATL, not in `utilconstr.facts/catalog` -> `:no-legal-basis`),
  `site-3` (`:site-not-verified` + `:utility-locate-incomplete`),
  `site-4` (`:utility-locate-incomplete`), `site-5` (20h < the JPN 168h
  minimum -> `:notification-lead-time-insufficient`), `site-6`
  (`:unresolved-safety-concern`), an op outside the closed four-op
  allowlist (`:unknown-op`), and the compromised-advisor probe
  (`:effect-not-propose` + `:forbidden-action-class`).

  Returns {:db :notifier :runs} -- `:runs` is one map per operation, each
  holding the raw langgraph run results, so `render` below reads real
  actor output rather than a hand-typed transcript."
  []
  (let [db (store/seed-db)
        notifier (notify/mock-notifier)
        actor (op/build db {:notifier notifier})
        rogue-actor (op/build db {:notifier notifier :advisor compromised-advisor})
        runs (atom [])
        step! (fn [a tid request approve?]
                (let [e (exec! a tid request)
                      ap (when approve? (approve! a tid))]
                  (swap! runs conj {:thread tid :op (:op request)
                                    :subject (:subject request)
                                    :exec e :approve ap})))]
    (step! actor "t01" {:op :log-site-record :subject "site-1"
                        :patch {:id "site-1" :trenching-percent-complete 40}} false)

    (step! actor "t02" {:op :schedule-construction-operation :subject "site-1"
                        :window {:proposed-start-date "2026-08-01"
                                 :proposed-end-date "2026-08-15"}
                        :notes "本管布設先行、その後宅内引込工事"} true)

    (step! actor "t03" {:op :flag-safety-concern :subject "site-1"
                        :concern-type :utility-strike
                        :concern-description "試掘溝で未表示のガス管を確認、埋設物損傷リスクの可能性。"} true)

    (step! actor "t04" {:op :log-site-record :subject "site-1"
                        :patch {:id "site-1" :safety-concern-unresolved? false}} false)

    (step! actor "t05" {:op :schedule-construction-operation :subject "site-1"
                        :window {:proposed-start-date "2026-08-08"
                                 :proposed-end-date "2026-08-22"}
                        :notes "埋設物防護措置を反映した改訂スケジュール"} true)

    (step! actor "t06" {:op :order-supplies :subject "site-1"
                        :items ["HDPE-pipe-150mm-100m" "trench-shoring-panels-10"]
                        :cost-usd 1200 :vendor "Local Utility Supply Co."} false)

    (step! actor "t07" {:op :order-supplies :subject "site-1"
                        :items ["directional-boring-rig-rental"]
                        :cost-usd 18000 :vendor "Heavy Equip Rentals"} true)

    (step! actor "t08" {:op :schedule-construction-operation :subject "site-7"
                        :window {:proposed-start-date "2026-09-01"
                                 :proposed-end-date "2026-09-20"}} true)

    (step! actor "t09" {:op :schedule-construction-operation :subject "site-8"
                        :window {:proposed-start-date "2026-09-10"
                                 :proposed-end-date "2026-09-25"}} true)

    ;; --- HARD holds: none of these ever reaches the approval node ---
    (step! actor "t10" {:op :schedule-construction-operation :subject "site-2" :window {}} false)
    (step! actor "t11" {:op :schedule-construction-operation :subject "site-3" :window {}} false)
    (step! actor "t12" {:op :schedule-construction-operation :subject "site-4" :window {}} false)
    (step! actor "t13" {:op :schedule-construction-operation :subject "site-5" :window {}} false)
    (step! actor "t14" {:op :schedule-construction-operation :subject "site-6" :window {}} false)
    (step! actor "t15" {:op :direct-equipment-command :subject "site-1"} false)
    (step! rogue-actor "t16" {:op :order-supplies :subject "site-1"
                              :items ["excavator-direct-control-link"]
                              :cost-usd 900 :vendor "Rogue Equipment Control Ltd."} false)

    {:db db :notifier notifier :runs @runs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (str "<code>" (esc (if (keyword? v) (name v) v)) "</code>"))

(defn- flag-cell [b]
  (if (true? b)
    "<span class=\"ok\">true</span>"
    "<span class=\"warn\">false</span>"))

(defn- or-dash [v]
  (if (nil? v) "<span class=\"muted\">—</span>" (esc v)))

(defn- tr [& cells]
  (str "        <tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n      <thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n      <tbody>\n"
       (str/join "\n" rows)
       "\n      </tbody>\n    </table>\n"))

(defn- audit-of [run] (get-in run [:exec :state :audit] []))

(defn- hold-fact-of [run]
  (last (filter #(= :governor-hold (:t %)) (audit-of run))))

(defn- advisor-confidence-of [run]
  (some #(when (= :advisor-proposal (:t %)) (:confidence %)) (audit-of run)))

;; ----------------------------- sections -----------------------------

(defn- site-row [{:keys [id name jurisdiction site-verified? utility-locate-completed?
                         notification-lead-hours-actual safety-concern-unresolved?
                         trenching-percent-complete status]}]
  (tr (kw id) (esc name) (esc jurisdiction)
      (flag-cell site-verified?)
      (flag-cell utility-locate-completed?)
      (str "<span class=\"num\">" (or-dash notification-lead-hours-actual) "</span>")
      (if (true? safety-concern-unresolved?)
        "<span class=\"critical\">unresolved</span>"
        "<span class=\"ok\">none open</span>")
      (str "<span class=\"num\">" (or-dash trenching-percent-complete) "</span>")
      (kw status)))

(defn- outcome-cell [{:keys [exec approve] :as run}]
  (let [hf (hold-fact-of run)]
    (cond
      hf (str "<span class=\"critical\">HARD hold · "
              (esc (str/join ", " (map name (:basis hf))))
              "</span>")
      approve (str "<span class=\"ok\">escalated → human-approved → committed</span>")
      (= :commit (get-in exec [:state :disposition]))
      "<span class=\"ok\">auto-committed (phase 3)</span>"
      :else "<span class=\"warn\">escalated · awaiting approval</span>")))

(defn- run-row [{:keys [thread op subject] :as run}]
  (tr (kw thread) (kw op) (kw subject)
      (str "<span class=\"num\">" (or-dash (advisor-confidence-of run)) "</span>")
      (outcome-cell run)))

(defn- hold-detail-rows [runs]
  (for [{:keys [thread subject op] :as run} runs
        :let [hf (hold-fact-of run)]
        :when hf
        v (:violations hf)]
    (tr (kw thread) (kw subject) (kw op) (kw (:rule v)) (esc (:detail v)))))

(defn- ledger-row [{:keys [t op subject disposition basis summary]}]
  (tr (kw t) (kw (or op :n-a)) (kw subject) (kw (or disposition :n-a))
      (cond
        (= :governor-hold t) (esc (str/join ", " (map name basis)))
        (seq basis) (str "<span class=\"muted\">" (count basis) " citation(s)</span>")
        :else "<span class=\"muted\">—</span>")
      (or-dash summary)))

(defn- artifact-row [r]
  (tr (kw (get r "record_id")) (esc (get r "kind"))
      (kw (get r "site_id")) (esc (get r "jurisdiction"))
      (flag-cell (get r "immutable"))))

(defn- jurisdiction-row [iso3]
  (let [{:keys [name threshold-model notification-lead-hours
                utility-locate-provenance traffic-control-provenance]} (facts/spec-basis iso3)]
    (tr (kw iso3) (esc name) (kw threshold-model)
        (str "<span class=\"num\">" (or-dash notification-lead-hours) "</span>")
        (str "<a href=\"" (esc utility-locate-provenance) "\">utility-locate</a> · "
             "<a href=\"" (esc traffic-control-provenance) "\">traffic-control</a>"))))

(defn- notify-row [{:keys [channel to subject message status]}]
  (tr (kw channel) (esc to)
      (if (= :sent status) "<span class=\"ok\">sent</span>" (kw status))
      (esc (or subject message))))

(def ^:private action-gate-rows
  ;; STATIC DESCRIPTION of this actor's own fixed contract -- transcribed
  ;; from `utilconstr.phase/phases` (`:writes`/`:auto` sets),
  ;; `utilconstr.governor/high-stakes`, `/closed-op-allowlist` and
  ;; `/supply-order-cost-threshold-usd`. It is DOCUMENTATION OF FIXED
  ;; BEHAVIOUR, NOT RUNTIME TELEMETRY -- unlike every other table on this
  ;; page, nothing here is read back from a live run. Keep it in step with
  ;; those two namespaces when they change.
  [(tr "<code>:log-site-record</code>"
       "<span class=\"ok\">phase-3 auto-commit when governor-clean</span>"
       "data logging only (utility-locate / trenching / pipe-laying progress); no capital or safety risk")
   (tr "<code>:schedule-construction-operation</code>"
       "<span class=\"warn\">ALWAYS human approval · never auto at any phase</span>"
       "site-verified? + utility-locate-completed? + jurisdiction legal-basis + notification lead time + no unresolved concern, all HARD-checked off the store's own ground truth")
   (tr "<code>:flag-safety-concern</code>"
       "<span class=\"warn\">ALWAYS human approval · never auto at any phase</span>"
       "site-verified? HARD-checked; on commit the notice is actually dispatched (mail + phone) to the site's safety-contact roster")
   (tr "<code>:order-supplies</code>"
       "<span class=\"warn\">phase-3 auto-commit only below 5000 USD and above the 0.6 confidence floor</span>"
       "site-verified? HARD-checked; cost is a SOFT, cost-scoped escalation, not permanent high-stakes membership")
   (tr "<code>anything else</code>"
       "<span class=\"critical\">HARD hold · un-overridable</span>"
       "the four ops above are a CLOSED allowlist; every proposal must also carry :effect :propose and must not carry an equipment-control / direct-actuation / tie-in- or energization-authorization-finalizing marker")])

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole operator-console document from the result of
  `run-demo!` (or any other real scenario over the same store)."
  [{:keys [db notifier runs]}]
  (let [sites (store/all-sites db)
        ledger (vec (store/ledger db))
        holds (filter #(= :governor-hold (:t %)) ledger)
        notice (get (first (store/safety-concern-flag-history db)) "document")
        coverage (facts/coverage)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-4220 · utility-line construction · operator console</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<div class=\"dds-ext-container\">\n"
     "<header class=\"bar\">\n"
     "  <h1 class=\"dads-heading\" data-size=\"32\">Construction of utility projects (ISIC 4220) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · coordination-only · every proposal is <code>:effect :propose</code></span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Construction sites</h2>\n"
     "    <p class=\"subtitle\">Build-time snapshot of <code>utilconstr.store</code> AFTER the run below, generated by <code>utilconstr.render-html</code> (<code>clojure -M:dev:render-html</code>). Every row is a seeded site; every column is a field of this actor's own site model.</p>\n"
     (table ["Site" "Name" "Jurisdiction" "site-verified?" "utility-locate-completed?"
             "Notification lead (h, actual)" "Safety concern" "Trenching % complete" "Status"]
            (map site-row sites))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Operations run through the actor</h2>\n"
     "    <p class=\"subtitle\">One row = one <code>langgraph</code> graph run (intake → advise → govern → decide → commit | hold | approval). Confidence is the Utility-Construction Advisor's own reported confidence for that proposal; the outcome is the disposition the run actually returned.</p>\n"
     (table ["Thread" "Op" "Site" "Advisor confidence" "Outcome"]
            (map run-row runs))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">HARD holds reached (" (count holds) ")</h2>\n"
     "    <p class=\"subtitle\">A HARD violation is un-overridable: the run is held at <code>:decide</code> and NEVER reaches the human-approval node. Rule names and the detail text below are the Utility-Construction Governor's own output, not a description of it.</p>\n"
     (table ["Thread" "Site" "Op" "Rule" "Governor detail"]
            (hold-detail-rows runs))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Action gate (Utility-Construction Governor + phase 3)</h2>\n"
     "    <p class=\"subtitle\">Fixed contract, not telemetry — transcribed from <code>utilconstr.phase</code> and <code>utilconstr.governor</code>. This actor never controls heavy equipment and never finalizes a utility-tie-in or energization authorization; that authority is the licensed utility engineer / site supervisor's exclusively.</p>\n"
     (table ["Op" "Gate" "Independent re-checks"] action-gate-rows)
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Audit ledger (this run — " (count ledger) " facts)</h2>\n"
     "    <p class=\"subtitle\">Append-only decision-fact log. Only <code>:commit</code> and <code>:hold</code> write here; an escalation on its own does not.</p>\n"
     (table ["Fact" "Op" "Site" "Disposition" "Basis" "Summary"]
            (map ledger-row ledger))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Coordination artifacts committed</h2>\n"
     "    <p class=\"subtitle\">Each committed proposal appends one immutable record with a jurisdiction-scoped sequence number (<code>utilconstr.registry</code>). These are DRAFTS/PROPOSALS — no real filing, dispatch, procurement or authorization happens.</p>\n"
     "    <h3 class=\"dads-heading\" data-size=\"18\">Site-record log</h3>\n"
     (table ["Record" "Kind" "Site" "Jurisdiction" "Immutable"]
            (map artifact-row (store/site-record-log-history db)))
     "    <h3 class=\"dads-heading\" data-size=\"18\">Schedule proposals</h3>\n"
     (table ["Record" "Kind" "Site" "Jurisdiction" "Immutable"]
            (map artifact-row (store/schedule-proposal-history db)))
     "    <h3 class=\"dads-heading\" data-size=\"18\">Safety-concern flags</h3>\n"
     (table ["Record" "Kind" "Site" "Jurisdiction" "Immutable"]
            (map artifact-row (store/safety-concern-flag-history db)))
     "    <h3 class=\"dads-heading\" data-size=\"18\">Supply-order proposals</h3>\n"
     (table ["Record" "Kind" "Site" "Jurisdiction" "Immutable"]
            (map artifact-row (store/supply-order-proposal-history db)))
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Safety-concern notice actually dispatched</h2>\n"
     "    <p class=\"subtitle\">Sent only AFTER a human approved the flag (<code>:flag-safety-concern</code> is never auto-eligible at any phase), to the site's own <code>:safety-contacts</code> roster, through the injected notifier — the mock transport here, a real Resend/Twilio transport in production.</p>\n"
     (table ["Channel" "To" "Status" "Subject / message"]
            (map notify-row (notify/sent-log notifier)))
     "    <h3 class=\"dads-heading\" data-size=\"18\">Notice document</h3>\n"
     "    <pre>" (esc notice) "</pre>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2 class=\"dads-heading\" data-size=\"24\">Jurisdiction legal basis</h2>\n"
     "    <p class=\"subtitle\">" (esc (:note coverage))
     " Covered: " (esc (str/join ", " (:covered-jurisdictions coverage)))
     ". A jurisdiction absent from this catalog has NO spec-basis — the advisor must not invent one, and the governor HARD-holds any schedule proposal that tries (see <code>site-2</code>, jurisdiction <code>ATL</code>, above).</p>\n"
     (table ["ISO3" "Name" "Threshold model" "Minimum notification lead (h)" "Primary sources"]
            (map jurisdiction-row (sort (keys facts/catalog))))
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>Generated from the real actor by <code>utilconstr.render-html</code>. Deterministic: no timestamps, no random, no wall-clock — reruns are byte-identical. This is a coordination-only actor: it proposes, logs, schedules and flags, and never dispatches heavy equipment or finalizes a tie-in / energization authorization.</p>\n"
     "</footer>\n"
     "</div>\n</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)
        ledger (store/ledger (:db result))]
    (spit out html :encoding "UTF-8")
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count (filter #(= :governor-hold (:t %)) ledger)) " HARD holds, "
                  (count (store/schedule-proposal-history (:db result))) " schedule proposals, "
                  (count (store/safety-concern-flag-history (:db result))) " safety-concern flags, "
                  (count (store/supply-order-proposal-history (:db result))) " supply orders, "
                  (count html) " chars)"))))
