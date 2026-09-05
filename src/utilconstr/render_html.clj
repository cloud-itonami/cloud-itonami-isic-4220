(ns utilconstr.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2: this repo previously had NO demo page
  and no generator at all. This namespace drives the REAL actor stack
  (`utilconstr.operation` -> `utilconstr.governor` -> `utilconstr.store`)
  and renders the page from what that run actually produced. Nothing on
  the page is hand-typed domain data: every site id, ground-truth
  boolean, jurisdiction threshold, coordination-artifact record id, hold
  rule and ledger row is read back out of `utilconstr.store` /
  `utilconstr.facts` after the graph has run.

  ## Provenance of every subject id

  Every subject id used below (`site-1`..`site-8`) is already present in
  `utilconstr.store/demo-data`, this repo's own seed -- no id is
  invented. This repo's own `utilconstr.sim` demo driver (`clojure
  -M:dev:run`, run BEFORE this file was written to confirm the real
  ledger shape) also uses exactly those seeded ids, so its scenario was
  a safe basis to adapt rather than something that had to be rewritten
  from scratch.

  ## Which scenario each site exercises

    site-1  JPN, fully clean ground truth -- the FULL clean lifecycle:
            `:log-site-record` (AUTO-COMMITS at phase 3) -> `:schedule-
            construction-operation` (always escalates; human approves)
            -> `:flag-safety-concern` (always escalates; human approves;
            the safety-concern notice is really dispatched through the
            injected mock Notifier to the site's 2-person contact
            roster, and the op flips the site's own `:safety-concern-
            unresolved?` ground-truth field to true) -> `:log-site-
            record` logging the concern's resolution (AUTO-COMMITS,
            flipping it back) -> `:order-supplies` below the cost
            threshold (AUTO-COMMITS) -> `:order-supplies` above it
            (escalates; human approves). site-1 is ALSO the target of
            the three adversarial probes below, so its row shows both
            commits and holds.
    site-7  USA (quantitative, 24-hour floor; site records 30h) --
            cross-jurisdiction clean schedule; escalates, human approves.
    site-8  DEU/EU (qualitative -- the law grounds no numeric lead-time
            and this actor refuses to invent one, so `utilconstr.facts/
            notification-lead-insufficient?` returns `:qualitative` and
            no HARD numeric check can fire). The human is therefore the
            only gate -- and here the human REJECTS, which is what puts
            the third ledger fact type (`:approval-rejected`) on the
            page.

  HARD holds, one per governor check, none of which ever reaches a human:

    site-2  ATL, a jurisdiction deliberately absent from `utilconstr.
            facts/catalog`               -> :no-legal-basis          (check 5)
    site-3  neither verified nor located -> :site-not-verified
                                            + :utility-locate-incomplete
                                                                     (checks 4+6)
    site-4  verified but locate open     -> :utility-locate-incomplete (check 6)
    site-5  JPN, records 20h < 168h      -> :notification-lead-time-insufficient
                                                                     (check 7)
    site-6  unresolved concern on file   -> :unresolved-safety-concern (check 8)
    site-1  op outside the closed 4-op allowlist
                                         -> :unknown-op              (check 1)

  The remaining two governor checks (2 and 3) exist as defence-in-depth
  against a COMPROMISED advisor, and this repo's own mock advisor never
  produces the shapes they catch -- so they are unreachable unless a
  misbehaving advisor is actually injected. `utilconstr.operation/build`
  already exposes `:advisor` as an injection seam for exactly this, so
  `rogue-advisor` below injects one through the real protocol. The
  governor is NOT stubbed in those two runs -- it really executes and
  really holds, which is the whole point of the check:

    site-1  advisor returns `:effect :dispatch-crew`
                                         -> :effect-not-propose      (check 2)
    site-1  advisor returns `:value {:equipment-control? true}`
                                         -> :forbidden-action-class  (check 3)

  ## Determinism

  Two consecutive runs are byte-identical: the store is a freshly seeded
  `MemStore`, `utilconstr.registry` derives every record id from a
  jurisdiction-scoped sequence counter (no clock, no random), the mock
  Notifier records no timestamps, and no wall-clock value is rendered
  into the page. Verify with a two-run diff.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [utilconstr.advisor :as advisor]
            [utilconstr.facts :as facts]
            [utilconstr.notify :as notify]
            [utilconstr.operation :as op]
            [utilconstr.store :as store]))

;; ----------------------------- driving the real actor -----------------------------

(def ^:private operator
  "The human operator context injected into every run -- phase 3
  (`supervised-coordination`), the most permissive phase this actor has."
  {:actor-id "op-1" :actor-role :site-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn- rogue-advisor
  "A DELIBERATELY MISBEHAVING advisor, injected through the real
  `utilconstr.advisor/Advisor` protocol seam `utilconstr.operation/build`
  already exposes. It exists only to reach governor checks 2 and 3, which
  are defence-in-depth against a compromised advisor and are therefore
  unreachable through the honest mock advisor. The governor itself is
  never stubbed -- it runs normally and rejects these proposals on its
  own, which is exactly the property the checks claim to have."
  [proposal]
  (reify advisor/Advisor
    (-advise [_ _store _request] proposal)))

(defn run-demo!
  "Runs a freshly seeded store through the scenario documented in the ns
  docstring, returning `{:db .. :notifier ..}`. Every value the page
  renders is produced here by the real graph -- `render` below only reads
  the store and the notifier's send log back out."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (op/build db {:notifier notifier})]

    ;; --- site-1: the full clean lifecycle -------------------------------
    (exec! actor "s1-log" {:op :log-site-record :subject "site-1"
                           :patch {:id "site-1" :trenching-percent-complete 40}})

    (exec! actor "s1-schedule" {:op :schedule-construction-operation :subject "site-1"
                                :window {:proposed-start-date "2026-08-01"
                                         :proposed-end-date   "2026-08-15"}
                                :notes "本管布設先行、その後宅内引込工事"})
    (approve! actor "s1-schedule")

    (exec! actor "s1-concern" {:op :flag-safety-concern :subject "site-1"
                               :concern-type :utility-strike
                               :concern-description "試掘溝で未表示のガス管を確認、埋設物損傷リスクの可能性。"})
    (approve! actor "s1-concern")

    (exec! actor "s1-resolve" {:op :log-site-record :subject "site-1"
                               :patch {:id "site-1" :safety-concern-unresolved? false}})

    (exec! actor "s1-supply-low" {:op :order-supplies :subject "site-1"
                                  :items ["HDPE-pipe-150mm-100m" "trench-shoring-panels-10"]
                                  :cost-usd 1200 :vendor "Local Utility Supply Co."})

    (exec! actor "s1-supply-high" {:op :order-supplies :subject "site-1"
                                   :items ["directional-boring-rig-rental"]
                                   :cost-usd 18000 :vendor "Heavy Equip Rentals"})
    (approve! actor "s1-supply-high")

    ;; --- cross-jurisdiction clean (USA, quantitative floor met) ---------
    (exec! actor "s7-schedule" {:op :schedule-construction-operation :subject "site-7"
                                :window {:proposed-start-date "2026-09-01"
                                         :proposed-end-date   "2026-09-20"}})
    (approve! actor "s7-schedule")

    ;; --- qualitative jurisdiction, human declines -----------------------
    (exec! actor "s8-schedule" {:op :schedule-construction-operation :subject "site-8"
                                :window {:proposed-start-date "2026-09-10"
                                         :proposed-end-date   "2026-09-25"}})
    (reject! actor "s8-schedule")

    ;; --- HARD holds, one per ground-truth / legal-basis check -----------
    (exec! actor "s2-schedule" {:op :schedule-construction-operation :subject "site-2" :window {}})
    (exec! actor "s3-schedule" {:op :schedule-construction-operation :subject "site-3" :window {}})
    (exec! actor "s4-schedule" {:op :schedule-construction-operation :subject "site-4" :window {}})
    (exec! actor "s5-schedule" {:op :schedule-construction-operation :subject "site-5" :window {}})
    (exec! actor "s6-schedule" {:op :schedule-construction-operation :subject "site-6" :window {}})

    ;; --- HARD hold: op outside the closed four-op allowlist -------------
    (exec! actor "s1-unknown" {:op :direct-equipment-command :subject "site-1"})

    ;; --- HARD holds reachable only via a compromised advisor ------------
    (let [bad-effect (op/build db {:notifier notifier
                                   :advisor (rogue-advisor
                                             {:summary    "crew dispatch"
                                              :rationale  "rogue advisor: real-world actuation effect"
                                              :cites      []
                                              :effect     :dispatch-crew
                                              :value      {:site-id "site-1"}
                                              :stake      nil
                                              :confidence 0.95})})]
      (exec! bad-effect "s1-bad-effect" {:op :log-site-record :subject "site-1"
                                         :patch {:id "site-1"}}))

    (let [bad-class (op/build db {:notifier notifier
                                  :advisor (rogue-advisor
                                            {:summary    "excavator command"
                                             :rationale  "rogue advisor: equipment-control marker"
                                             :cites      ["site-1"]
                                             :effect     :propose
                                             :value      {:site-id "site-1"
                                                          :equipment-control? true}
                                             :stake      nil
                                             :confidence 0.95})})]
      (exec! bad-class "s1-bad-class" {:op :order-supplies :subject "site-1"}))

    {:db db :notifier notifier}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- as-str
  "Basis entries are keywords (rule names, patch keys) for some ops and
  long citation strings for others -- normalise without throwing on nil."
  [x]
  (cond (nil? x) "" (keyword? x) (name x) :else (str x)))

(defn- truncate [n s]
  (if (> (count s) n) (str (subs s 0 n) "…") s))

(defn- facts-for-site [ledger site-id]
  (filterv #(= (:subject %) site-id) ledger))

(defn- hold? [f] (contains? #{:governor-hold :approval-rejected} (:t f)))

(defn- rules-of
  "The distinct rule keywords a set of hold facts fired, in first-seen
  order. `:basis` carries `(mapv :rule violations)` for a governor hold
  and `[:approver-rejected]` for a human rejection."
  [facts]
  (->> facts (filter hold?) (mapcat :basis) (map as-str) distinct vec))

;; ----------------------------- site table -----------------------------

(defn- bool-cell [v yes no]
  (if (true? v)
    (str "<span class=\"ok\">" yes "</span>")
    (str "<span class=\"critical\">" no "</span>")))

(defn- lead-cell
  "The site's own recorded notification lead time against its
  jurisdiction's legal minimum, using the SAME three-valued function the
  governor uses (`utilconstr.facts/notification-lead-insufficient?`).
  A `:qualitative` jurisdiction is reported as such -- this actor never
  invents an hour count to make it look automatable."
  [{:keys [jurisdiction notification-lead-hours-actual] :as site}]
  (let [sb      (facts/spec-basis jurisdiction)
        minimum (:notification-lead-hours sb)
        verdict (facts/notification-lead-insufficient? jurisdiction site)]
    (cond
      (nil? sb)
      "<span class=\"muted\">no spec-basis on file</span>"

      (= :qualitative verdict)
      "<span class=\"muted\">qualitative — no numeric floor in law</span>"

      (nil? notification-lead-hours-actual)
      (str "<span class=\"muted\">not recorded (min " (esc minimum) "h)</span>")

      (true? verdict)
      (str "<span class=\"critical\">" (esc notification-lead-hours-actual)
           "h &lt; " (esc minimum) "h</span>")

      :else
      (str "<span class=\"ok\">" (esc notification-lead-hours-actual)
           "h ≥ " (esc minimum) "h</span>"))))

(defn- site-row [ledger {:keys [id name jurisdiction site-verified?
                                utility-locate-completed?
                                safety-concern-unresolved? status] :as site}]
  (let [mine      (facts-for-site ledger id)
        committed (count (filter #(= :committed (:t %)) mine))
        held      (count (filter hold? mine))
        rules     (rules-of mine)]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td>"
                 "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td>"
                 "<td>%s</td><td>%s</td></tr>")
            (esc id) (esc name) (esc jurisdiction)
            (bool-cell site-verified? "verified" "not verified")
            (bool-cell utility-locate-completed? "located" "locate open")
            (if (true? safety-concern-unresolved?)
              "<span class=\"critical\">unresolved</span>"
              "<span class=\"ok\">none open</span>")
            (lead-cell site)
            (str "<code>" (esc (as-str status)) "</code>")
            (if (pos? committed)
              (str "<span class=\"ok\">" committed "</span>")
              "<span class=\"muted\">0</span>")
            (if (pos? held)
              (str "<span class=\"critical\">HARD hold &middot; " held "</span> "
                   "<span class=\"muted\">" (esc (str/join ", " rules)) "</span>")
              "<span class=\"muted\">0</span>"))))

;; ----------------------------- ledger table -----------------------------

(def ^:private fact-label
  "The THREE fact types `utilconstr.operation` actually appends to the
  store ledger. `:approval-granted`, `:approval-requested` and
  `:advisor-proposal` are deliberately absent -- those go only to the
  in-memory `:audit` channel and never reach `store/append-ledger!`, so
  branching on them here would produce dead code."
  {:committed         ["ok"       "committed"]
   :governor-hold     ["critical" "HARD hold"]
   :approval-rejected ["warn"     "rejected by human"]})

(defn- ledger-row [{:keys [t op subject basis violations]}]
  (let [[cls label] (get fact-label t ["muted" (as-str t)])
        basis-text  (str/join " ; " (map as-str basis))
        detail      (str/join " / " (keep :detail violations))]
    (format (str "        <tr><td><span class=\"%s\">%s</span></td>"
                 "<td><code>%s</code></td><td><code>%s</code></td>"
                 "<td title=\"%s\">%s</td></tr>")
            cls (esc label)
            (esc (as-str op)) (esc subject)
            (esc (if (seq detail) detail basis-text))
            (esc (truncate 110 basis-text)))))

;; ----------------------------- governor checks -----------------------------

(def ^:private governor-checks
  ;; Static description of this actor's own EIGHT fixed governor checks,
  ;; transcribed from `utilconstr.governor`'s ns docstring and check
  ;; functions. This is documentation-of-code (the checks are a fixed
  ;; contract, not runtime telemetry) -- but the "fired" column beside it
  ;; is NOT static: it is counted from the ledger this run produced.
  [[:unknown-op "op outside the closed four-op allowlist" "structural"]
   [:effect-not-propose "proposal `:effect` is not literally `:propose`" "structural"]
   [:forbidden-action-class "equipment-control / direct-actuation / tie-in / energization marker set" "structural"]
   [:site-not-verified "site's own `:site-verified?` ground truth is not true" "ground truth"]
   [:no-legal-basis "no OFFICIAL spec-basis cited for the jurisdiction" "legal basis"]
   [:utility-locate-incomplete "site's own `:utility-locate-completed?` is not true" "ground truth"]
   [:notification-lead-time-insufficient "recorded lead time below the jurisdiction's legal minimum" "recomputed"]
   [:unresolved-safety-concern "site's own `:safety-concern-unresolved?` is true" "ground truth"]])

(defn- governor-check-row [ledger [rule description kind]]
  (let [n (->> ledger (filter hold?) (mapcat :basis) (filter #(= % rule)) count)]
    (format (str "        <tr><td><code>:%s</code></td><td>%s</td><td>%s</td>"
                 "<td>%s</td></tr>")
            (esc (name rule)) (esc description) (esc kind)
            (if (pos? n)
              (str "<span class=\"critical\">fired &times;" n "</span>")
              "<span class=\"muted\">not reached this run</span>"))))

;; ----------------------------- op / phase gate -----------------------------

(def ^:private op-gate-rows
  ;; Static description of this actor's own closed op contract, from
  ;; `utilconstr.phase/phases` (phase 3 `:auto` set) and
  ;; `utilconstr.governor/high-stakes`. Documentation of fixed behaviour,
  ;; not runtime telemetry, so it is legitimately hand-described.
  ["        <tr><td><code>:log-site-record</code></td><td><span class=\"ok\">phase-3 AUTO-COMMIT when the governor is clean</span></td><td>data logging only, no capital or safety risk</td></tr>"
   "        <tr><td><code>:schedule-construction-operation</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span></td><td>coordinates potential heavy-equipment dispatch near traffic and other buried utilities</td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto at any phase</span></td><td>surfacing a utility-strike / collapse / gas-leak concern is never an automatic call</td></tr>"
   "        <tr><td><code>:order-supplies</code></td><td><span class=\"ok\">phase-3 AUTO-COMMIT below</span> / <span class=\"warn\">human approval above</span> the cost threshold</td><td>soft, cost-scoped gate — not a permanent high-stakes membership</td></tr>"])

;; ----------------------------- artifact histories -----------------------------

(defn- artifact-row [record]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (get record "record_id"))
          (esc (get record "kind"))
          (esc (get record "site_id"))
          (esc (get record "jurisdiction"))))

(defn- notice-row [{:keys [channel to subject message]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (as-str channel)) (esc to) (esc (or subject message))))

;; ----------------------------- page -----------------------------

(defn- section [title lead headers rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" % "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

(defn render
  "Renders the operator console from a store `db` and `notifier` that
  have already been driven by `run-demo!`. Reads only real state --
  `store/all-sites`, `store/ledger`, the four coordination-artifact
  histories and the notifier's send log."
  [db notifier]
  (let [ledger    (vec (store/ledger db))
        sites     (store/all-sites db)
        committed (count (filter #(= :committed (:t %)) ledger))
        holds     (count (filter #(= :governor-hold (:t %)) ledger))
        rejected  (count (filter #(= :approval-rejected (:t %)) ledger))
        artifacts (concat (store/site-record-log-history db)
                          (store/schedule-proposal-history db)
                          (store/safety-concern-flag-history db)
                          (store/supply-order-proposal-history db))]
    (str
     "<!doctype html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-4220 &middot; utility-line construction coordination</title>\n"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Construction of utility projects (ISIC 4220) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · coordination-only · every proposal is <code>:effect :propose</code> · scheduling and safety concerns always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>This run</h2>\n"
     "    <p class=\"banner\">Generated at build time by <code>utilconstr.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) from a real run of the actor graph over the "
     "seeded site set in <code>utilconstr.store/demo-data</code>. "
     "<strong>" committed "</strong> committed · "
     "<strong>" holds "</strong> HARD holds that never reached a human · "
     "<strong>" rejected "</strong> declined by a human · "
     "<strong>" (count artifacts) "</strong> coordination artifacts.</p>\n"
     "    <p class=\"muted\">This actor never dispatches heavy equipment and never finalizes a "
     "utility tie-in or energization authorization — that authority is the licensed utility "
     "engineer / site supervisor's exclusively. Committing here means a coordination artifact "
     "was logged, nothing more.</p>\n"
     "  </section>\n"

     (section "Sites"
              (str "Ground truth as the store holds it after the run. The governor re-reads these "
                   "fields itself before any proposal may commit — it never trusts the advisor's "
                   "own confidence.")
              ["Site" "Name" "Juris." "Verified" "Utility locate" "Safety concern"
               "Notification lead vs legal min" "Status" "Committed" "Held"]
              (map (partial site-row ledger) sites))

     (section "Governor checks (Utility-Construction Governor)"
              (str "All eight are HARD — a human approver cannot override any of them. "
                   "The rule list is this actor's fixed contract; the right-hand column is "
                   "counted from the ledger this run actually produced.")
              ["Rule" "What it checks" "Kind" "This run"]
              (map (partial governor-check-row ledger) governor-checks))

     (section "Op gate (rollout phase 3 — supervised-coordination)"
              (str "Two independent layers agree that scheduling and safety-concern flagging are "
                   "never automatic: <code>utilconstr.phase</code> keeps them out of every phase's "
                   "auto set, and <code>utilconstr.governor/high-stakes</code> escalates them "
                   "unconditionally.")
              ["Op" "Gate at phase 3" "Why"]
              op-gate-rows)

     (section "Audit ledger (this run)"
              (str "Append-only decision facts — the only three types the commit and hold nodes "
                   "ever write. Hover a row for the governor's full detail or the citation it "
                   "committed against.")
              ["Fact" "Op" "Site" "Basis"]
              (map ledger-row ledger))

     (section "Coordination artifacts"
              (str "What committing actually produced: jurisdiction-scoped, sequence-derived draft "
                   "records — no clock, no random, so they are stable across runs.")
              ["Record" "Kind" "Site" "Jurisdiction"]
              (map artifact-row artifacts))

     (section "Safety-concern notices dispatched"
              (str "Sent through the injected Notifier at commit time, and only after a human "
                   "approved the flag — <code>:flag-safety-concern</code> is never auto-eligible "
                   "at any phase.")
              ["Channel" "To" "Subject / message"]
              (map notice-row (notify/sent-log notifier)))

     "</main>\n"
     "<footer>\n"
     "  <p class=\"muted\">cloud-itonami-isic-4220 — open business blueprint. "
     "No invented usage or revenue metrics; every value above is read back out of the actor's "
     "own store after a real run.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db notifier]} (run-demo!)
        html (render db notifier)]
    (io/make-parents out)
    (spit out html)
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count (store/all-sites db)) " sites, "
                  (count (notify/sent-log notifier)) " notices dispatched)"))))
