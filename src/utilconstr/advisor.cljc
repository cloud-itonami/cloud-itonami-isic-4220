(ns utilconstr.advisor
  "Utility-Construction Advisor client -- the *contained intelligence
  node* for the utility-line-construction-project OPERATIONS
  COORDINATION actor.

  It normalizes site-record logging (utility-locate/trenching-progress
  data), drafts a construction-operation SCHEDULE PROPOSAL (trenching/
  pipe-laying/tie-in-crew scheduling, never a finalized tie-in or
  energization-authorization decision), drafts a safety-concern FLAG
  (utility-strike / excavation-collapse / gas-leak), and drafts an
  aggregate/pipe/cable/equipment supply-order PROPOSAL. CRITICAL: it is a
  smart-but-untrusted advisor, and it is SCOPED -- it holds NO heavy-
  equipment-control authority and NO utility-tie-in/energization-
  authorization authority (that is the licensed utility engineer / site
  supervisor's exclusively). It returns a *proposal* (with a rationale +
  the fields it cited), NEVER a committed record, a real mail/phone send,
  a real procurement order, or a real tie-in/energization authorization.
  Every output carries `:effect :propose` and is censored downstream by
  `utilconstr.governor` before anything touches the SSoT -- see README
  `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the legal-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- see `utilconstr.governor`
     :value      map            ; the coordination-artifact payload
     :stake      kw|nil         ; the op itself (drives high-stakes gating) or nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [utilconstr.facts :as facts]
            [utilconstr.store :as store]
            [langchain.model :as model]))

(defn- log-site-record
  "Site-record-log normalization -- the LLM only normalizes/validates the
  patch (utility-locate / trenching / pipe-laying-progress data); it does
  not invent the site, its jurisdiction or its ground-truth fields. High
  confidence, low stakes -- `:stake nil`, the lowest-risk op in this
  actor's closed allowlist."
  [_db {:keys [patch]}]
  {:summary    (str "現場記録更新: " (pr-str (keys patch)))
   :rationale  "入力patchの正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :propose
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- schedule-construction-operation
  "Draft a construction-operation SCHEDULE PROPOSAL -- a proposed
  trenching/pipe-laying/tie-in-crew phase window (never a finalized
  tie-in or energization-authorization decision; see `utilconstr.
  governor` ns docstring). `:stake :schedule-construction-operation` --
  ALWAYS escalates to a human (see README `Actuation`)."
  [db {:keys [subject window notes]}]
  (let [a (store/site db subject)
        iso3 (:jurisdiction a)
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式legal-basisが見つかりません -- スケジュール提案不可")
       :rationale  "utilconstr.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :propose
       :value      {:site-id subject :jurisdiction iso3 :window window :notes notes :spec-basis nil}
       :stake      :schedule-construction-operation
       :confidence 0.2}
      {:summary    (str subject " 向け掘削・配管/ケーブル敷設スケジュール提案 (" (:owner-authority sb) ")"
                        (when a (str " (site=" (:name a) ")")))
       :rationale  (str "埋設物事前確認basis: " (:utility-locate-basis sb)
                       " / 交通規制basis: " (:traffic-control-basis sb)
                       " / site-verified?=" (:site-verified? a)
                       " / utility-locate-completed?=" (:utility-locate-completed? a)
                       " / notification-lead-hours-actual=" (:notification-lead-hours-actual a))
       :cites      [(:utility-locate-basis sb) (:utility-locate-provenance sb)
                    (:traffic-control-basis sb) (:traffic-control-provenance sb)]
       :effect     :propose
       :value      {:site-id subject :jurisdiction iso3 :window window :notes notes
                    :spec-basis (:traffic-control-provenance sb)}
       :stake      :schedule-construction-operation
       :confidence (if (and a (:site-verified? a) (:utility-locate-completed? a)) 0.9 0.3)})))

(defn- flag-safety-concern
  "Draft a SAFETY-CONCERN FLAG -- surfacing a utility-strike /
  excavation-collapse / gas-leak concern for human review. `:stake
  :flag-safety-concern` -- ALWAYS escalates to a human, unconditionally,
  regardless of confidence (see README `Actuation` + `utilconstr.
  governor` ns docstring `high-stakes`)."
  [db {:keys [subject concern-type concern-description]}]
  (let [a (store/site db subject)
        sb (facts/spec-basis (:jurisdiction a))]
    {:summary    (str subject ": 安全性懸念を検出（" (name (or concern-type :utility-strike)) "）"
                      (when a (str " (site=" (:name a) ")")))
     :rationale  (if sb
                   (str "関連basis: " (:utility-locate-basis sb))
                   "現場記録または法域spec-basisが見つかりません")
     :cites      (if sb [(:utility-locate-basis sb)] [])
     :effect     :propose
     :value      {:site-id subject
                  :concern-type (or concern-type :utility-strike)
                  :concern-description concern-description
                  :subject-line (str "[至急] " (:name a) " 安全性懸念のお知らせ")
                  :body (str (:name a) "の現場で安全性懸念（" (name (or concern-type :utility-strike))
                            "）が検出されました。詳細を確認し対応を検討してください。")
                  :message (str (:name a) "、安全性懸念が検出されました。至急ご確認ください。")}
     :stake      :flag-safety-concern
     :confidence 0.9}))

(defn- order-supplies
  "Draft an aggregate/pipe/cable/equipment SUPPLY-ORDER PROPOSAL.
  `:stake :order-supplies` -- escalates when `:cost-usd` exceeds
  `utilconstr.governor/supply-order-cost-threshold-usd`, or when
  confidence is low (see `utilconstr.governor`)."
  [db {:keys [subject items cost-usd vendor]}]
  (let [a (store/site db subject)]
    {:summary    (str subject " 向け資材/機材発注提案 (" (pr-str items) ", "
                      cost-usd " USD, vendor=" vendor ")"
                      (when a (str " (site=" (:name a) ")")))
     :rationale  (if a (str "site-verified?=" (:site-verified? a)) "現場記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :propose
     :value      {:site-id subject :items items :cost-usd cost-usd :vendor vendor}
     :stake      :order-supplies
     :confidence (if (and a (:site-verified? a)) 0.85 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :log-site-record                 (log-site-record db request)
    :schedule-construction-operation (schedule-construction-operation db request)
    :flag-safety-concern             (flag-safety-concern db request)
    :order-supplies                  (order-supplies db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :propose :value {} :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは水道・下水道・ガス・電力・通信の管路/ケーブル埋設工事プロジェクトの"
       "運行調整（オペレーションズ・コーディネーション）エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) :effect(常に:propose) "
       ":value(提案内容のmap) "
       ":stake(:log-site-record|:schedule-construction-operation|:flag-safety-concern|"
       ":order-supplies のいずれか) :confidence(0..1)。\n"
       "重要: あなたは重機を直接操作するコマンドや、供用中埋設物へのタイイン（切替接続）"
       "・エネルギー化（送電・通ガス・通水開始）を確定させる提案を絶対に作成しては"
       "いけません（免許を持つ公益事業技術者/現場監督の専権事項）。登録されていない"
       "法域の要件を絶対に創作してはいけません。legal-basisが無い場合は:citesを空にし"
       "confidenceを上げないこと。"))

(defn- facts-for [st {:keys [subject]}]
  {:site (store/site st subject)})

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Utility-Construction Governor
  escalates/holds -- an LLM hiccup can never auto-schedule an operation,
  auto-flag (or suppress) a safety concern, or auto-order supplies."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :propose))
          (update :value #(or % {})))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :propose :value {} :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :advisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
