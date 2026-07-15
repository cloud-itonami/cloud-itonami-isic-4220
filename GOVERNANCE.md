# Governance

`cloud-itonami-4220` is an OSS open-business blueprint for utility-line-
construction-project operations coordination -- coordination-only, never
equipment control or tie-in/energization authorization.

## Maintainers

Maintainers may merge changes that preserve these invariants:
- this actor never holds heavy-equipment-control authority.
- this actor never holds utility-tie-in or energization-authorization
  authority -- that remains the licensed utility engineer / site
  supervisor's exclusively.
- every proposal this actor's advisor produces carries `:effect
  :propose`, and the Utility-Construction Governor remains independent
  of the advisor.
- hard policy violations (unknown op, non-`:propose` effect, forbidden
  action class, unverified site, missing legal basis, incomplete utility
  locate, insufficient notification lead time, unresolved safety
  concern) cannot be overridden by human approval.
- `:schedule-construction-operation` and `:flag-safety-concern` always
  require human sign-off, at every phase, unconditionally.
- every proposal, sign-off, log entry and notification path is
  auditable.
- sensitive operating and personal data stays outside Git.

## Decision Records

Architecture decisions should be documented (an ADR or equivalent) when
changing the trust model, storage contract, closed op-allowlist, business
model, operator certification or license.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is
a separate trust mark and should require security, safety, audit and
data-flow review.

Certified operators can lose certification for:
- bypassing the Utility-Construction Governor's hard checks or the
  closed op-allowlist
- attempting to extend this actor's authority into equipment control or
  utility-tie-in/energization-authorization decisions
- mishandling sensitive data
- misrepresenting certification status
- failing to respond to security or safety incidents
