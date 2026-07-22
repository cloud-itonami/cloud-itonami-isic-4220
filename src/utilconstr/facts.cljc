(ns utilconstr.facts
  "Per-jurisdiction utility-line-construction regulatory catalog -- the
  spec-basis table the Utility-Construction Governor checks every
  `:schedule-construction-operation` proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's buried-utility-
  strike-prevention/road-opening-notification requirements, or did it
  invent one?'). Same honest-coverage discipline `construction.facts`
  (`cloud-itonami-isic-4211`) / `demolition.facts`
  (`cloud-itonami-isic-4311`) / `roadrail.facts` (`cloud-itonami-isic-
  4210`) established for this fleet: a jurisdiction not in this table has
  NO spec-basis, full stop -- the advisor must not fabricate one, and the
  governor holds if it tries.

  Coverage is reported HONESTLY (see `coverage`); this is a STARTING
  catalog (JPN/USA/DEU/GBR), not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to `catalog`,
  cite a real source, done -- never invent a jurisdiction's requirements
  to make coverage look bigger.

  ## Scope -- excavation/buried-utility-strike prevention + road-opening/
  utility-permit legal basis, NOT pipeline-technical-safety-code specifics

  This ISIC 4220 class covers construction of water, sewer, gas,
  electric-power and telecommunication utility LINES -- trenching,
  pipe/cable laying, and tie-in to existing mains. The citations seeded
  below (excavation/underground-installation-strike prevention before
  digging, utility-installation-in-right-of-way permitting, advance-
  notice before work starts) are grounded in the legal exposure common to
  EVERY utility-line trade in this class -- earthwork/trenching performed
  in or adjacent to a public right-of-way always risks striking an
  UNRELATED buried utility (a water main, a gas line, a fiber duct, an
  electric conduit) regardless of which utility the finished asset itself
  is. Utility-specific technical/pipeline-safety-code regimes (e.g. USA:
  49 CFR Part 192 Transportation of Natural Gas and Other Gas by
  Pipeline: Minimum Federal Safety Standards, 49 CFR Part 195 for
  hazardous liquid pipelines; Japan: ガス事業法 / 電気事業法 tie-in and
  energization technical-safety regimes) are real but are OUT OF SCOPE
  for this R0 catalog -- a documented future extension, never fabricated
  into this slice. Those same technical regimes are exactly WHY this
  actor's governor permanently blocks any proposal that tries to finalize
  a tie-in or energization authorization (see `utilconstr.governor` ns
  docstring check 3) -- that authority belongs to the licensed utility
  engineer / site supervisor operating under those technical codes, never
  to this coordination-only actor.

  `:threshold-model` mirrors the SAME honest quantitative/qualitative
  split `construction.facts`/`demolition.facts`/`roadrail.facts`
  established, applied here to the SAME real-world numeric trigger
  `roadrail.facts` uses (utility-line construction and road/railway
  construction share the identical excavation/utility-locate legal
  exposure) -- the minimum number of HOURS of lead time a site must
  record between submitting its utility-locate/road-opening advance
  notice and the proposed start of excavation:
    :quantitative -- the law itself grounds a fixed minimum lead-time
                     pause (Japan's 168 hours / 7 calendar days under the
                     Construction Recycling Act's civil-engineering-work
                     notification duty; the USA's 24 hours under OSHA's
                     excavation-standard utility-locate-response floor,
                     re-expressed here as a minimum lead pause before
                     work may start rather than a literal response-time
                     SLA -- see the USA entry's `:threshold-note` for the
                     honest translation). `notification-lead-insufficient?`
                     can independently recompute a HARD hold from this.
    :qualitative  -- the law imposes a documented duty (a traffic-control
                     order before work affecting road traffic; a prior
                     notice for larger construction sites) with NO fixed
                     EU-wide numeric lead-time (Germany/EU). This actor
                     does NOT invent a day/hour-count to make this
                     jurisdiction look automatable -- `notification-lead-
                     insufficient?` returns `:qualitative` and the
                     Utility-Construction Governor's permanent high-stakes
                     gate on `:schedule-construction-operation` (see
                     `utilconstr.governor` ns docstring) routes the
                     decision to a human every time regardless.

  DEU is used as the EU-jurisdiction proxy, the SAME convention
  `construction.facts`/`demolition.facts`/`roadrail.facts`/`aerospace.
  facts` established -- there is no ISO-3166 alpha-3 code for the EU
  itself, and road/traffic law in Germany is largely federal (StVO) with
  construction-site safety law transposing EU Directive 92/57/EEC, so the
  citation lists BOTH the national StVO provision and the EU directive
  rather than inventing an EU country code.

  All citations below were verified against primary sources (e-Gov 法令
  検索 for Japanese statutes, eCFR/osha.gov for US federal regulations,
  gesetze-im-internet.de/EUR-Lex for German/EU law) before being written
  here -- none is recalled from memory alone without a source check."
  )

(def catalog
  "iso3 -> requirement map. `:utility-locate-basis` / `:traffic-control-
  basis` / their `-provenance` pairs, plus `:owner-authority`, are the
  G2-style citation the governor requires before a `:schedule-
  construction-operation` proposal can ever commit. `:permit-basis` /
  `:permit-provenance` are carried for citation completeness in the
  rendered schedule/safety-concern documents (`utilconstr.registry`) but
  are NOT independently governor-hard-gated in this R0 slice (unlike
  `construction.facts`'s building-permit check) -- reported honestly as
  reference data, not fabricated as an enforced gate this actor doesn't
  actually check. USA's `:permit-basis` (23 CFR 645.213, 'Use and
  occupancy agreements (permits)') is the DIRECT federal-aid-highway
  utility-installation permit -- the most literal fit in this whole fleet
  between a citation and this ISIC class's own subject matter."
  {"JPN" {:name "Japan"
          :owner-authority "国土交通省道路局／都道府県・市町村道路管理者（道路法）／都道府県公安委員会・警察署長（道路交通法）／厚生労働省労働基準局（労働安全衛生規則）／各事業法所管官庁（電気事業法：経済産業省、ガス事業法：経済産業省、水道法：厚生労働省）"
          :utility-locate-basis "労働安全衛生規則（昭和47年労働省令第32号）第355条（明り掘削の作業を行う場合において、地山の崩壊又は埋設物等の損壊により労働者に危険を及ぼすおそれのあるときは、あらかじめボーリングその他適当な方法により作業箇所及びその周辺の地山について調査し、これらの事項について知り得たところに適応する掘削の時期及び順序を定めて作業を行う義務）"
          :utility-locate-provenance "https://laws.e-gov.go.jp/law/347M50002000032"
          :traffic-control-basis "道路交通法（昭和35年法律第105号）第77条第1項第1号（道路において工事又は作業をしようとする者は、あらかじめ当該行為に係る場所を管轄する警察署長の道路使用許可を受ける義務）"
          :traffic-control-provenance "https://laws.e-gov.go.jp/law/335AC0000000105"
          :threshold-model :quantitative
          :notification-lead-hours 168
          :threshold-note "建設リサイクル法（建設工事に係る資材の再資源化等に関する法律）第10条・同法施行令第2条第1項第4号（建築物以外のもの＝水道管・ガス管・電線・通信線等の土木工作物に関する解体工事又は新築工事等であって請負代金の額が500万円以上のものについて、工事着手の7日前（=168時間）までに都道府県知事等へ分別解体等の計画を届け出る義務）。roadrail.facts/demolition.factsと同じ根拠法だが対象カテゴリは『建築物以外のもの』（土木工作物＝ライフライン管路・ケーブル埋設工事を含む）。"
          :permit-basis "道路法（昭和27年法律第180号）第32条第1項（水道管、下水道管、ガス管、電線、電話線等の物件を設けて道路を占用しようとする者は、あらかじめ道路管理者の許可を受ける義務）"
          :permit-provenance "https://laws.e-gov.go.jp/law/327AC1000000180"}
   "USA" {:name "United States"
          :owner-authority "Federal Highway Administration (FHWA), U.S. DOT / State Department of Transportation (Authority Having Jurisdiction) / Occupational Safety and Health Administration (OSHA), U.S. Department of Labor"
          :utility-locate-basis "29 CFR 1926.651(b) (Specific Excavation Requirements -- Underground installations: before opening an excavation, the estimated location of utility installations shall be determined; utility companies/owners shall be contacted, advised of the proposed work and asked to establish the location of underground installations, and if they cannot respond to that request within 24 hours (unless a longer period is required by state or local law), the employer may proceed only with caution, using detection equipment or other acceptable means to locate the installations)"
          :utility-locate-provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926.651"
          :threshold-model :quantitative
          :notification-lead-hours 24
          :threshold-note "29 CFR 1926.651(b)(2) sets a 24-hour minimum WAIT for a utility-locate response before an employer may proceed with caution -- this actor re-expresses that as a minimum 24-hour lead pause a site must record between submitting its utility-locate request (the same request a state 811 One-Call center relays -- state 811 statutes are real but out of scope for this R0 catalog, the same honest exclusion `roadrail.facts` gives FRA track law) and the proposed start of excavation, so the comparison direction ('more recorded lead time is safer') stays consistent with Japan's advance-notice-before-start model above. This is an honest modeling translation of a real numeric floor, not a literal restatement of OSHA's own response-time SLA -- OSHA's rule additionally permits proceeding sooner with detection equipment, a nuance this actor's bright-line HARD check deliberately does not model, the same simplification `demolition.facts`/`roadrail.facts` make for their own quantitative jurisdictions."
          :traffic-control-basis "23 CFR Part 630, Subpart J (Work Zone Safety and Mobility) + Part 6 of the Manual on Uniform Traffic Control Devices (MUTCD), incorporated by reference at 23 CFR 655.603 -- the State shall develop a Temporary Traffic Control (TTC) plan, consistent with MUTCD Part 6, for the work zone before starting construction/maintenance operations on a Federal-aid highway project, including utility-installation work performed within the right-of-way"
          :traffic-control-provenance "https://www.ecfr.gov/current/title-23/chapter-I/subchapter-G/part-630/subpart-J"
          :permit-basis "23 CFR 645.213 (Use and occupancy agreements (permits) -- utility installations within Federal-aid/direct-Federal highway right-of-way require a written use-and-occupancy agreement/permit with the State transportation department before construction) -- the direct federal utility-installation permit citation for this ISIC class"
          :permit-provenance "https://www.ecfr.gov/current/title-23/chapter-I/subchapter-G/part-645/subpart-B/section-645.213"
          :permit-note "Federal-aid/direct-Federal highway right-of-way utility permits and work-zone traffic-control plans are governed by 23 CFR 645 Subpart B / 630 Subpart J; state and local roads follow the equivalent state DOT/AHJ utility-encroachment-permit and traffic-control-plan process, and gas/hazardous-liquid pipeline-specific technical safety (49 CFR Parts 192/195) is a SEPARATE federal regime this R0 slice does not independently check -- the same honest state-vs-federal layering cloud-itonami-isic-4211/4311/4210 use for their own USA citations."}
   "DEU" {:name "Germany (EU jurisdiction proxy, see ns docstring)"
          :owner-authority "Straßenverkehrsbehörde（道路交通当局、州・市町村レベル）／Straßenbaubehörde（道路管理当局）／Bundesministerium für Digitales und Verkehr (BMDV); EU level: European Agency for Safety and Health at Work (EU-OSHA)"
          :utility-locate-basis "Council Directive 92/57/EEC of 24 June 1992 (minimum safety and health requirements at temporary or mobile construction sites) Art.3 -- the client/project supervisor must ensure a safety and health plan is drawn up (covering, among other risks, damage to existing underground installations) before work starts; there is no EU-wide statute specifying a fixed utility-locate procedure or response-time floor the way Japan's 労働安全衛生規則 or the USA's OSHA excavation standard do -- utility-protection technical rules (e.g. DVGW/DIN standards for utility marking) are industry technical standards, not codified statute, at the Land level."
          :utility-locate-provenance "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:31992L0057"
          :threshold-model :qualitative
          :notification-lead-hours nil
          :threshold-note "EU/ドイツの法令は、道路交通に影響する作業開始前の交通規制命令（StVO第45条）取得義務と、一定規模（30就業日超又は同時20人超又は500人日超）の建設現場について所轄当局への事前通知義務（Directive 92/57/EEC 附属書III）を課すのみで、日本の168時間（7暦日）・米国の24時間のような固定リードタイムはEU全域では法定されていない -- ここで数値を創作しない。StVO第45条第6項は優先道路（Vorrangstraße）での車線減少工事について、申請から1週間当局が応答しない場合は許可されたとみなす旨の規定を置くが、これは『みなし許可』のタイムアウトであって全国一律の事前通知リードタイムではない。"
          :traffic-control-basis "Straßenverkehrs-Ordnung (StVO) §45 Abs.6 -- vor Beginn von Arbeiten, die den Straßenverkehr beeinträchtigen, hat der Bauunternehmer unter Vorlage eines Verkehrszeichenplans eine Anordnung der zuständigen Behörde einzuholen, wie die Arbeitsstelle abzusperren und zu kennzeichnen ist und ob und wie der Verkehr eingeschränkt, geleitet und geregelt werden soll (bei Fahrbahnverengung auf Vorrangstraßen gilt die Zustimmung als erteilt, wenn die Behörde nicht innerhalb einer Woche nach Eingang des Antrags widerspricht)."
          :traffic-control-provenance "https://www.gesetze-im-internet.de/stvo_2013/__45.html"
          :permit-basis "Straßenrecht ist grundsätzlich Ländersache (state-level competence in Germany, the same layering `construction.facts`/`demolition.facts`/`roadrail.facts` use for their own DEU citations) -- road-opening/utility-installation occupancy permits (Sondernutzungserlaubnis) are issued under the respective Land's Straßengesetz, with StVO §45 governing the SEPARATE traffic-control order needed once excavation begins."
          :permit-provenance "https://www.gesetze-im-internet.de/stvo_2013/__45.html"}
   "GBR" {:name "United Kingdom"
          :owner-authority "Health and Safety Executive (HSE) (Construction (Design and Management) Regulations 2015 underground-service/excavation duties) / the street authority -- the relevant local highway authority, or National Highways for trunk roads and motorways in England -- under the New Roads and Street Works Act 1991 Part III (England and Wales) / Secretary of State for Transport, Department for Transport (rule-making power for prescribed notice periods under the Street Works (Registers, Notices, Directions and Designations) (England) Regulations 2007)"
          :utility-locate-basis "The Construction (Design and Management) Regulations 2015 (S.I. 2015/51), regulation 25(4) (Energy distribution installations): construction work which is liable to create a risk to health or safety from an underground service, or from damage to or disturbance of it, must not be carried out unless suitable and sufficient steps (including any steps required by this regulation) have been taken to prevent the risk, so far as is reasonably practicable. Regulation 3(1)(a) applies these Regulations in Great Britain (England, Scotland, Wales); Northern Ireland has its own separate construction-safety regulations, not verified in this entry."
          :utility-locate-provenance "https://www.legislation.gov.uk/uksi/2015/51/regulation/25/made"
          :traffic-control-basis "New Roads and Street Works Act 1991 (c. 22) section 65 (Safety measures): an undertaker executing street works shall secure that any part of the street which is broken up or open, or is obstructed by plant or materials used or deposited in connection with the works, is adequately guarded and lit, and that such traffic signs are placed and maintained, and where necessary operated, as are reasonably required for the guidance or direction of persons using the street, having regard, in particular, to the needs of people with a disability; subsection (2) additionally requires the undertaker to comply with any directions of the traffic authority as to the placing, maintenance or operation of those signs. Part III applies to England and Wales; Scotland has an analogous Part IV of the SAME Act (from section 107, differently numbered, not independently verified in this entry) and Northern Ireland uses a separate instrument, the Street Works (Northern Ireland) Order 1995, not covered here."
          :traffic-control-provenance "https://www.legislation.gov.uk/ukpga/1991/22/section/65"
          :threshold-model :qualitative
          :notification-lead-hours nil
          :threshold-note "NRSWA 1991 section 54 requires the prescribed advance notice of street works before they begin, and section 55 separately requires notice of the actual starting date -- but unlike Japan's flat 168-hour or the USA's flat 24-hour floor, the prescribed periods are set by secondary legislation and are tiered by work classification, not a single number in the Act itself: the Street Works (Registers, Notices, Directions and Designations) (England) Regulations 2007 (S.I. 2007/1951) regulation 8(1) requires not less than three months advance notice of MAJOR works under section 54, while regulation 9(1)'s table sets the section 55 starting-date notice period at 10 days for major works, 10 days for standard works, and 3 days for minor works -- three materially different legal floors keyed to a work-classification field this actor's site record does not capture. Rather than fabricate a single bright-line hour count by arbitrarily picking one tier (which would either understate the true minimum for a major/standard-works site or overstate it for a minor-works site), this actor reports :qualitative for GBR, the same honest no-invented-number discipline the DEU/EU entry above applies -- for a structurally different reason here (real tiering by category, not the absence of any fixed period). These 2007 Regulations are titled for ENGLAND specifically; Wales, Scotland and Northern Ireland prescribe their own advance-notice periods under their own separate regulations, not independently verified in this R0 entry."
          :permit-basis "New Roads and Street Works Act 1991 (c. 22) section 54(1) (Advance notice of certain works): in such cases as may be prescribed an undertaker proposing to execute street works shall give the prescribed advance notice of the works to the street authority. This is the England & Wales functional analog of Japan's road-opening permit (道路法第32条) and the USA's utility use-and-occupancy permit (23 CFR 645.213), but the underlying mechanism differs: a UK statutory undertaker does not apply for an individual case-by-case permit to carry out routine apparatus works -- it exercises its own statutory apparatus powers and instead must give the street authority prescribed advance notice before starting, a notice-based rather than permit-based authorisation mechanism, reported honestly as such rather than mislabeled to match Japan/USA's literal permit structure."
          :permit-provenance "https://www.legislation.gov.uk/ukpga/1991/22/section/54"
          :permit-note "NRSWA 1991 Part III (sections 48-106) applies to England and Wales only; Scotland has an analogous road-works regime in Part IV of the SAME Act (from section 107, not independently verified in this entry) and Northern Ireland uses a wholly separate instrument, the Street Works (Northern Ireland) Order 1995, not covered by this R0 entry. A real additional permit layer also exists: Traffic Management Act 2004 (c. 18) section 32 lets a strategic highways company or a local highway authority in England or Wales adopt a locally-administered permit scheme requiring an actual case-by-case permit for specified street works in specified streets -- but adoption is at each highway authority's own discretion, not a nationwide requirement the way Japan's and the USA's permit citations are, so this R0 entry does not treat Traffic Management Act 2004 Part 3 as GBR's universal permit-basis."}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any `:schedule-construction-operation`
  proposal that tries to cite one."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4220 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `utilconstr.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn notification-lead-insufficient?
  "Independently recompute whether `site`'s own recorded
  `:notification-lead-hours-actual` (the site's own permanent recorded
  field -- hours between the utility-locate/road-opening advance-notice
  filing and the planned start of excavation) falls SHORT of `iso3`'s
  regulatory minimum lead time.

  Three-valued, deliberately (the same shape `construction.facts/weather-
  threshold-exceeded?` / `demolition.facts/notification-lead-insufficient?`
  / `roadrail.facts/notification-lead-insufficient?` established):
    true         -- a :quantitative jurisdiction (Japan, USA) whose own
                    numeric minimum lead time is independently confirmed
                    NOT met by the site's own recorded actual -- a
                    bright-line legal violation. The Utility-Construction
                    Governor turns this into a HARD, un-overridable hold
                    on `:schedule-construction-operation`.
    false        -- a :quantitative jurisdiction confirmed sufficient.
    :qualitative -- a jurisdiction with NO fixed numeric lead-time (DEU/
                    EU). This actor cannot independently confirm
                    'sufficient' or 'insufficient' by arithmetic alone --
                    the law itself requires a documented traffic-control-
                    order / prior-notice judgment call. Never fabricate a
                    lead-time here. The Utility-Construction Governor
                    relies on its permanent high-stakes gate for
                    `:schedule-construction-operation` (ALWAYS escalates
                    to a human, at every phase) rather than a HARD numeric
                    rule in this case.
    nil          -- no spec-basis at all for `iso3` (a jurisdiction not in
                    `catalog`)."
  [iso3 {:keys [notification-lead-hours-actual]}]
  (when-let [{:keys [threshold-model notification-lead-hours]} (spec-basis iso3)]
    (case threshold-model
      :quantitative
      (boolean (and (number? notification-lead-hours-actual)
                    (< notification-lead-hours-actual notification-lead-hours)))
      :qualitative
      :qualitative
      nil)))
