# Administration access and catalog audit

The database membership is the authority for business administration. Identity's
Owner, Manager and Staff roles permit entry to the administration UI and follow
current active memberships through `TeamRoleSync`; they do not grant another
branch's privileges. Global PlatformAdmin authority is checked against the current
persisted role, account lockout and session security stamp on every operation.
The authentication provider is read again, so a circuit cannot keep a cached actor.

Owners may create branches in their business. A branch's business cannot change:
its resources, service assignments and appointment history remain in the same
business. A business service's business also cannot change. Owners/platform admins
may manage its definition; a manager must currently manage all its active linked
branches before changing a shared service definition or status. Each destination
branch and selected staff resource is separately checked against current records.

Managers and owners manage schedules in their own branch. Staff membership does
not permit editing another provider's availability or changing service
qualifications. Existing rules and their destination scopes are checked separately
before edits. Blackout rules do not create or remove provider qualifications.
Dates, time ranges and today's elapsed time are validated on the server.

Identity profiles and account suspension are global. Branch managers manage team
membership rather than changing shared customer profiles or locking their accounts.
They may create a new account with a membership in an authorized branch, or add an
existing account by exact email without seeing the global directory. Removing a
member deactivates that branch's resources, preserves historical references, and is
blocked while the provider has active upcoming appointments. Membership restoration
reuses an archived row because the branch/user identifier pair remains unique.
Membership changes and Identity role synchronization share a transaction; role
synchronization failures cannot be reported as successful changes. A serializable
transaction protects removal of a branch's last owner.

Platform role changes preserve membership-derived roles. Self lockout/demotion and
removal or suspension of the last active platform administrator are blocked.
Role changes, account suspension/reactivation and password changes invalidate old
sessions through security stamps. Global account actions use fresh Identity scopes.

Business and catalog slugs use bounded lowercase ASCII words separated by single
hyphens. Archived identifiers reserve uniqueness. Names/categories/descriptions,
service duration (5–1440 minutes), nonnegative prices fitting decimal(18,2), working
hours and supported time zones are checked on the server. Category kind is immutable;
renaming updates archived references atomically and editing preserves inactive
status. Pausing a business preserves each child's independent archived state; the
shared active-parent query filters stop paused descendants from being booked.

Regression coverage is in `AdminAccessScopeTests`, `PlatformAdministrationTests`
and the authorization assertions in `AdminReviewsTests`. These cover current-role
query predicates, revoked/locked/stamped session conditions, authentication-state
replacement, archived-label scope retention, invalid membership role assignment,
server validation, archived identifier reservation and last-active-admin decisions.
Build, execution and browser/API verification are coordinated by the root audit.

Historical appointment and incoming/outgoing review scopes ignore active catalog
filters while retaining their own soft-delete predicate and current session/member
authorization. Archived branches and resources can therefore supply history labels
without restoring a removed membership. Dashboard management links use persisted
role/membership checks rather than cached role claims.

`AdministrationGraphIntegrationTests` exercises these rules against an initialized
disposable PostgreSQL database via `BENOBAT_TEST_DB`. Its fixtures roll back every
insert and update. It checks suspension/restoration, independent child archival,
provider membership/account state, cross-business links, existing catalog copies,
archived history labels and membership revocation with real query results.

The JSON management API shares the same service-definition boundary and preserves
membership-derived roles when changing global grants. Membership/Identity changes
and last-owner/last-admin checks use serializable transactions with conflict
responses on competing writes. Its archived history scopes retain current tenant
membership authorization. `ApiManagementAuthorizationTests` adds PostgreSQL
behavior tests for shared services and owner counting, plus the production global
role and administrator guard decisions.

Focused verification passed with PostgreSQL enabled: 18 tests across
`ApiManagementAuthorizationTests`, `MobileApiSecurityTests` and
`AdministrationGraphIntegrationTests`, with no failures or skips. This exposed and
fixed an EF query-filter expression bug: an untyped null constant combined with
an explicit `DeletedAt == null` predicate became `WHERE FALSE`. The shared filter
now uses a `DateTimeOffset?` null, and a regression checks the actual translation.
Independent fixture actors also use isolated context accessors so ambient HTTP
context state cannot overwrite another actor during permission checks.
