(ns utilconstr.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL actor stack -- `utilconstr.store/seed-db` ->
  `utilconstr.operation/build` (the langgraph-clj StateGraph) ->
  `utilconstr.governor` -> `utilconstr.registry` -> `utilconstr.notify`
  -- through one deterministic coordination episode and renders whatever
  that run actually produced. Nothing on the page is hand-written
  domain data: every site id, ground-truth field, record id, HARD-hold
  rule + detail string, safety-concern notice document and notifier send
  result below is read back out of the store / the graph's own `:audit`
  channel after the run.

  The scenario is adapted from this repo's own `utilconstr.sim` demo
  driver (`clojure -M:dev:run`, run and its output inspected BEFORE this
  file was written), with two additions the sim does not exercise: the
  `:forbidden-action-class` structural check (fed a `:log-site-record`
  patch carrying an `:equipment-control? true` marker -- the governor
  checks the proposal's own `:value` independently of the advisor) and
  the self-inflicted `:unresolved-safety-concern` hold that follows
  immediately after a safety-concern flag commits (the flag sets the
  site's own `:safety-concern-unresolved?` ground truth, so the very
  next schedule proposal for that site is HARD-held until a subsequent
  `:log-site-record` resolves it).

  Determinism: the mock advisor and mock notifier are deterministic, no
  timestamps are recorded anywhere in this actor's facts/records, sites
  are sorted by id and the jurisdiction catalog is sorted by ISO3 -- so
  two consecutive runs against the same seed are byte-identical.

  What is NOT rendered as store state, on purpose: `:approval-requested`
  and `:approval-granted` are emitted ONLY to the graph's in-memory
  `:audit` channel; `utilconstr.operation`'s `:commit`/`:hold` nodes
  append just `:committed` / `:governor-hold` / `:approval-rejected` to
  the SSoT ledger. The approval-queue section below is therefore
  labelled as the in-memory audit channel, and the ledger section never
  branches on a fact type the store cannot contain.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin :as skin]
            [langgraph.graph :as g]
            [utilconstr.facts :as facts]
            [utilconstr.governor :as governor]
            [utilconstr.notify :as notify]
            [utilconstr.operation :as op]
            [utilconstr.phase :as phase]
            [utilconstr.store :as store]))

(def ^:private operator
  "The same operator context `utilconstr.sim` uses -- phase 3
  (supervised-coordination), acting as the site supervisor."
  {:actor-id "op-1" :actor-role :site-supervisor :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

;; ----------------------------- the real run -----------------------------

(defn run-demo!
  "One coordination episode against a freshly seeded MemStore. Returns
  `{:db :notifier :audit}` -- `:audit` is the concatenation of each
  thread's FINAL graph state `:audit` channel (the in-memory decision
  trace, a superset of what reaches the ledger).

  Every subject driven below (`site-1`..`site-8`) is a member of
  `utilconstr.store/demo-data` -- no fabricated ids."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (op/build db {:notifier notifier})
        audit    (atom [])
        collect! (fn [r] (swap! audit into (:audit (:state r))) r)
        ;; auto-commit or HARD hold -- never reaches a human
        run!     (fn [tid request] (collect! (exec! actor tid request)))
        ;; escalates to a human, who approves; the resumed state carries
        ;; the whole thread's audit, so only the resumed run is collected
        run+ok!  (fn [tid request]
                   (exec! actor tid request)
                   (collect! (approve! actor tid)))]

    ;; --- structural HARD holds (governor checks 3 and 1) -------------
    ;; A patch carrying an :equipment-control? marker. The mock advisor
    ;; passes the patch straight through as the proposal's :value; the
    ;; governor rejects it on its own, un-overridably.
    (run! "t1" {:op :log-site-record :subject "site-1"
                :patch {:id "site-1" :equipment-control? true}})
    ;; An op outside the closed four-op allowlist.
    (run! "t2" {:op :direct-equipment-command :subject "site-1"})

    ;; --- site-1 (JPN) full lifecycle ---------------------------------
    (run! "t3" {:op :log-site-record :subject "site-1"
                :patch {:id "site-1" :trenching-percent-complete 40}})
    (run+ok! "t4" {:op :schedule-construction-operation :subject "site-1"
                   :window {:proposed-start-date "2026-08-01"
                            :proposed-end-date   "2026-08-15"}
                   :notes "本管布設先行、その後宅内引込工事"})
    (run+ok! "t5" {:op :flag-safety-concern :subject "site-1"
                   :concern-type :utility-strike
                   :concern-description "試掘溝で未表示のガス管を確認、埋設物損傷リスクの可能性。"})
    ;; committing the flag set site-1's own :safety-concern-unresolved?
    ;; ground truth -- the next schedule proposal HARD-holds on it
    (run! "t6" {:op :schedule-construction-operation :subject "site-1" :window {}})
    (run! "t7" {:op :log-site-record :subject "site-1"
                :patch {:id "site-1" :safety-concern-unresolved? false}})
    (run+ok! "t8" {:op :schedule-construction-operation :subject "site-1"
                   :window {:proposed-start-date "2026-08-08"
                            :proposed-end-date   "2026-08-22"}
                   :notes "埋設物防護措置を反映した改訂スケジュール"})
    (run! "t9" {:op :order-supplies :subject "site-1"
                :items ["HDPE-pipe-150mm-100m" "trench-shoring-panels-10"]
                :cost-usd 1200 :vendor "Local Utility Supply Co."})
    (run+ok! "t10" {:op :order-supplies :subject "site-1"
                    :items ["directional-boring-rig-rental"]
                    :cost-usd 18000 :vendor "Heavy Equip Rentals"})

    ;; --- one ground-truth HARD hold per remaining failure-mode site --
    (run! "t11" {:op :schedule-construction-operation :subject "site-2" :window {}})
    (run! "t12" {:op :schedule-construction-operation :subject "site-3" :window {}})
    (run! "t13" {:op :schedule-construction-operation :subject "site-4" :window {}})
    (run! "t14" {:op :schedule-construction-operation :subject "site-5" :window {}})
    (run! "t15" {:op :schedule-construction-operation :subject "site-6" :window {}})

    ;; --- cross-jurisdiction: USA (quantitative) / DEU (qualitative) --
    (run+ok! "t16" {:op :schedule-construction-operation :subject "site-7"
                    :window {:proposed-start-date "2026-09-01"
                             :proposed-end-date   "2026-09-20"}})
    (run+ok! "t17" {:op :schedule-construction-operation :subject "site-8"
                    :window {:proposed-start-date "2026-09-10"
                             :proposed-end-date   "2026-09-25"}})

    {:db db :notifier notifier :audit @audit}))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- label [x] (if (keyword? x) (name x) (str x)))

(defn- bool-cell [v]
  (if (true? v)
    "<span class=\"ok\">yes</span>"
    "<span class=\"warn\">no</span>"))

(defn- last-fact-for [ledger site-id]
  (last (filter #(= (:subject %) site-id) ledger)))

(defn- status-cell
  "Only the three fact types `utilconstr.operation` actually appends to
  the SSoT ledger (`:committed` from the `:commit` node,
  `:governor-hold`/`:approval-rejected` from the `:hold` node) are
  branched on here -- `:approval-granted`/`:approval-requested` never
  reach the store."
  [ledger site-id]
  (let [f (last-fact-for ledger site-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f))
      (str "<span class=\"ok\">committed</span> <span class=\"muted\">"
           (esc (label (:op f))) "</span>")
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (label (or (first (:basis f)) :unknown))) "</span>")
      (= :approval-rejected (:t f))
      "<span class=\"warn\">approval rejected</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- site-row [ledger {:keys [id name jurisdiction site-verified?
                                utility-locate-completed?
                                safety-concern-unresolved?
                                notification-lead-hours-actual
                                trenching-percent-complete status]}]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
               "<td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>")
          (esc id) (esc name) (esc jurisdiction)
          (bool-cell site-verified?)
          (bool-cell utility-locate-completed?)
          (if (true? safety-concern-unresolved?)
            "<span class=\"critical\">unresolved</span>"
            "<span class=\"ok\">none open</span>")
          (if (number? notification-lead-hours-actual)
            (str "<span class=\"num\">" notification-lead-hours-actual "</span>")
            "<span class=\"muted\">not recorded</span>")
          (if (number? trenching-percent-complete)
            (str "<span class=\"num\">" trenching-percent-complete "%</span>")
            "<span class=\"muted\">&mdash;</span>")
          (esc (label status))
          (status-cell ledger id)))

;; --- governor checks: the 8 HARD rules, with "did it fire" derived ---

(def ^:private hard-checks
  "The eight HARD checks in `utilconstr.governor/check`, in the priority
  order that ns docstring defines. The rule keywords are the ones the
  governor itself puts in a violation's `:rule`; whether each one FIRED
  is derived from this run's ledger, never asserted here."
  [[:unknown-op                        "op outside the closed four-op allowlist" "structural"]
   [:effect-not-propose                "proposal :effect is anything other than :propose" "structural"]
   [:forbidden-action-class            "proposal :value carries :equipment-control? / :direct-actuation? / :finalizes-tie-in-authorization? / :finalizes-energization-authorization?" "structural"]
   [:site-not-verified                 "site's own :site-verified? ground truth is not true" "ground truth"]
   [:no-legal-basis                    "no OFFICIAL utilconstr.facts citation for the jurisdiction" "citation"]
   [:utility-locate-incomplete         "site's own :utility-locate-completed? ground truth is not true" "ground truth"]
   [:notification-lead-time-insufficient "recorded :notification-lead-hours-actual below the jurisdiction's quantitative legal minimum" "recomputed"]
   [:unresolved-safety-concern         "site's own :safety-concern-unresolved? ground truth is true" "ground truth"]])

(defn- check-row [fired [rule what kind]]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (name rule)) (esc what) (esc kind)
          (if (contains? fired rule)
            "<span class=\"critical\">fired this run</span>"
            "<span class=\"muted\">not exercised by this scenario</span>")))

(defn- hold-row [{:keys [op subject violations confidence]}]
  (str/join "\n"
            (for [{:keys [rule detail]} violations]
              (format (str "        <tr><td><code>%s</code></td><td><code>%s</code></td>"
                           "<td><code>%s</code></td><td>%s</td><td><span class=\"num\">%s</span></td></tr>")
                      (esc (label rule)) (esc (label op)) (esc subject)
                      (esc detail) (esc confidence)))))

(defn- approval-row [{:keys [t op subject reason by confidence]}]
  (format (str "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td>"
               "<td>%s</td><td>%s</td></tr>")
          (if (= :approval-granted t)
            "<span class=\"ok\">granted</span>"
            "<span class=\"warn\">requested</span>")
          (esc (label op)) (esc subject)
          (esc (if (= :approval-granted t) (str "by " by) (label (or reason "-"))))
          (if (number? confidence)
            (str "<span class=\"num\">" confidence "</span>")
            "<span class=\"muted\">&mdash;</span>")))

(defn- ledger-row [{:keys [t op subject basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (case t
            :committed      "<span class=\"ok\">committed</span>"
            :governor-hold  "<span class=\"critical\">governor-hold</span>"
            :approval-rejected "<span class=\"warn\">approval-rejected</span>"
            (esc (label t)))
          (esc (label op)) (esc subject)
          (if (= :governor-hold t)
            (str "<code>" (esc (str/join ", " (map label basis))) "</code>")
            ;; a committed fact's :basis is the proposal's :cites -- full
            ;; statute text, far too long for a table cell; the count is
            ;; reported and the sources themselves listed in the
            ;; jurisdiction catalog below
            (str "<span class=\"muted\">" (count basis) " cited source(s)</span>"))))

(defn- record-row [r]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (get r "record_id")) (esc (get r "kind"))
          (esc (get r "site_id")) (esc (get r "jurisdiction"))))

(defn- send-row
  "One row per entry in the mock notifier's own send log
  (`utilconstr.notify/sent-log`) -- mail entries carry `:subject`, phone
  entries carry `:message`."
  [{:keys [channel to status subject message]}]
  (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (label channel)) (esc to)
          (if (= :sent status)
            "<span class=\"ok\">sent</span>"
            (str "<span class=\"critical\">" (esc (label status)) "</span>"))
          (esc (or subject message))))

(defn- jurisdiction-row [[iso3 {:keys [name threshold-model notification-lead-hours
                                       utility-locate-provenance
                                       traffic-control-provenance
                                       permit-provenance]}]]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
               "<td>%s</td></tr>")
          (esc iso3) (esc name) (esc (label threshold-model))
          (if (number? notification-lead-hours)
            (str "<span class=\"num\">" notification-lead-hours " h</span>")
            "<span class=\"muted\">no fixed numeric floor (never fabricated)</span>")
          (str/join "<br>"
                    (for [u [utility-locate-provenance traffic-control-provenance permit-provenance]
                          :when u]
                      (str "<a href=\"" (esc u) "\">" (esc u) "</a>")))))

(defn- gate-rows
  "The per-op gate, described from this actor's OWN vars -- the phase-3
  `:auto` set, the governor's permanent `high-stakes` set and its cost
  threshold, rather than numbers retyped by hand."
  []
  (let [auto   (get-in phase/phases [3 :auto])
        stakes governor/high-stakes]
    (for [op (sort (vec governor/closed-op-allowlist))]
      (format "        <tr><td><code>%s</code></td><td>%s</td></tr>"
              (esc (name op))
              (cond
                (contains? stakes op)
                "<span class=\"warn\">ALWAYS human approval &middot; never auto-eligible at any phase</span>"
                (and (contains? auto op) (= :order-supplies op))
                (str "<span class=\"ok\">phase-3 auto-commit when governor-clean</span>"
                     " <span class=\"muted\">&middot; escalates above "
                     governor/supply-order-cost-threshold-usd
                     " USD or below confidence "
                     governor/confidence-floor "</span>")
                (contains? auto op)
                "<span class=\"ok\">phase-3 auto-commit when governor-clean</span>"
                :else
                "<span class=\"warn\">human approval</span>")))))

(defn render
  "Renders the console from a `run-demo!` result. Every value comes back
  out of the store, the graph audit channel or the mock notifier."
  [{:keys [db notifier audit]}]
  (let [ledger      (vec (store/ledger db))
        sites       (store/all-sites db)
        holds       (filter #(= :governor-hold (:t %)) ledger)
        fired       (set (mapcat :basis holds))
        approvals   (filter #(#{:approval-requested :approval-granted} (:t %)) audit)
        concerns    (store/safety-concern-flag-history db)
        notice      (some #(get % "document") concerns)
        sends       (notify/sent-log notifier)]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<title>Operator console &middot; cloud-itonami-isic-4220 &middot; utilconstr</title>"
     "<style>" (skin/dds+skin) "</style></head><body>\n"
     "<div class=\"container\">\n"
     "<header class=\"bar\">\n"
     "  <h1>Construction of utility projects (ISIC 4220) — Operator Console</h1>\n"
     "</header>\n"
     "<p class=\"subtitle\"><span class=\"badge\">coordination-only</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">every proposal is <code>:effect :propose</code></span></p>\n"
     "<p class=\"muted\">Build-time snapshot generated by <code>clojure -M:dev:render-html</code> "
     "(<code>utilconstr.render-html</code>). It drives the real actor — "
     "<code>utilconstr.store/seed-db</code> → <code>utilconstr.operation/build</code> "
     "(langgraph-clj StateGraph) → <code>utilconstr.governor</code> — and prints back what that run "
     "produced. This actor never dispatches heavy equipment and never finalizes a utility tie-in or "
     "energization authorization; that authority is the licensed utility engineer / site supervisor's "
     "exclusively.</p>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Sites</h2>\n"
     "    <p class=\"muted\">Ground-truth fields the Utility-Construction Governor re-checks independently "
     "of any proposal. <em>Last ledger fact</em> is the most recent SSoT fact for that site in this run.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Site</th><th>Name</th><th>Jur.</th><th>Verified</th><th>Utility locate</th>"
     "<th>Safety concern</th><th>Notice lead (h)</th><th>Trenching</th><th>Seed status</th>"
     "<th>Last ledger fact</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial site-row ledger) sites)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate</h2>\n"
     "    <p class=\"muted\">Derived from <code>utilconstr.governor/closed-op-allowlist</code>, "
     "<code>/high-stakes</code>, <code>/supply-order-cost-threshold-usd</code>, "
     "<code>/confidence-floor</code> and <code>utilconstr.phase/phases</code> — the operator context "
     "for this run is phase 3 (<em>supervised-coordination</em>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (gate-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Governor HARD checks</h2>\n"
     "    <p class=\"muted\">All eight are HARD: a human approver cannot override them, and a HARD hold "
     "never reaches the approval node at all. <em>Fired this run</em> is computed from the "
     "<code>:basis</code> of this run's <code>:governor-hold</code> facts.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Fires when</th><th>Kind</th><th>This run</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial check-row fired) hard-checks)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <p class=\"muted\"><code>:effect-not-propose</code> is defence-in-depth against a compromised or "
     "malfunctioning advisor. Every code path in this repo's own "
     "<code>utilconstr.advisor</code> emits <code>:effect :propose</code>, so this scenario cannot make it "
     "fire without swapping in a deliberately broken advisor — reported honestly as unexercised rather than "
     "faked.</p>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD holds in this run</h2>\n"
     "    <p class=\"muted\">Rule, detail and confidence are the governor's own violation maps, read back "
     "out of the ledger — not restated here.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Rule</th><th>Op</th><th>Site</th><th>Governor detail</th><th>Advisor conf.</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hold-row holds)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Human-approval queue (graph <code>:audit</code> channel)</h2>\n"
     "    <p class=\"muted\">These facts live in the StateGraph's in-memory <code>:audit</code> channel only. "
     "<code>utilconstr.operation</code> appends just <code>:committed</code>, <code>:governor-hold</code> "
     "and <code>:approval-rejected</code> to the SSoT ledger, so an approval is visible here and as the "
     "resulting commit below — never as an <code>:approval-granted</code> row in the ledger.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Event</th><th>Op</th><th>Site</th><th>Reason / approver</th><th>Conf.</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map approval-row approvals)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (SSoT, append-only)</h2>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Site</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Coordination artifacts</h2>\n"
     "    <p class=\"muted\">Jurisdiction-scoped, append-only drafts built by "
     "<code>utilconstr.registry</code>. Every one is a PROPOSAL record; none is a filing with any real "
     "regulator.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record</th><th>Kind</th><th>Site</th><th>Jurisdiction</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map record-row (concat (store/site-record-log-history db)
                                            (store/schedule-proposal-history db)
                                            (store/safety-concern-flag-history db)
                                            (store/supply-order-proposal-history db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     (if notice
       (str
        "  <section class=\"card\">\n"
        "    <h2>Safety-concern notice</h2>\n"
        "    <p class=\"muted\">The document <code>utilconstr.registry/render-safety-concern-notice</code> "
        "actually produced for the approved flag, citing the jurisdiction's utility-locate legal basis "
        "inline.</p>\n"
        "    <pre>" (esc notice) "</pre>\n"
        "  </section>\n")
       "")

     "  <section class=\"card\">\n"
     "    <h2>Notice dispatch</h2>\n"
     "    <p class=\"muted\">Sent over both channels to the site's <code>:safety-contacts</code> roster by "
     "<code>utilconstr.notify/dispatch-safety-concern-notice!</code> through the deterministic "
     "<code>mock-notifier</code> — the real Resend/Twilio transports are the injection seam, not used "
     "here. This fires only after a human approved the flag; it is never reachable from an auto-commit.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Channel</th><th>To</th><th>Status</th><th>Subject / spoken message</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map send-row sends)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Jurisdiction catalog</h2>\n"
     "    <p class=\"muted\">"
     (esc (:note (facts/coverage)))
     "</p>\n"
     "    <table>\n"
     "      <thead><tr><th>ISO3</th><th>Name</th><th>Threshold model</th><th>Legal min. lead</th>"
     "<th>Primary sources</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map jurisdiction-row (sort-by key facts/catalog))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>Regenerate with <code>clojure -M:dev:render-html</code>. Deterministic: no timestamps, mock "
     "advisor and mock notifier, sites sorted by id — two consecutive runs against the same seed are "
     "byte-identical.</p>\n"
     "  <p><code>cloud-itonami/cloud-itonami-isic-4220</code> · module <code>utilconstr</code></p>\n"
     "</footer>\n"
     "</div>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out    (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html   (render result)
        f      (java.io.File. ^String out)]
    (when-let [parent (.getParentFile f)] (.mkdirs parent))
    (spit f html)
    (println "wrote" out
             "(" (count (store/ledger (:db result))) "ledger facts,"
             (count (filter #(= :governor-hold (:t %)) (store/ledger (:db result)))) "HARD holds,"
             (count (notify/sent-log (:notifier result))) "notice sends )")))
