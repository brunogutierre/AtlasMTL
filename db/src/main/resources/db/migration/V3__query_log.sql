-- One row per question, for costs, budgets and the public /stats page (which shows aggregates
-- only). The visitor's address is stored as a keyed hash, never in clear.
create table query_log (
  id             bigint generated always as identity primary key,
  asked_at       timestamptz not null default now(),
  visitor        text        not null,      -- HMAC of the client address
  question       text        not null,
  outcome        text        not null check (outcome in ('done', 'error', 'cancelled')),
  cached         boolean     not null,
  tools          jsonb       not null,      -- names of the tools called, in order
  input_tokens   integer     not null,
  output_tokens  integer     not null,
  cost_usd       numeric(10, 6) not null,
  latency_ms     integer     not null
);
create index query_log_asked_at_idx on query_log (asked_at);
create index query_log_visitor_idx on query_log (visitor, asked_at);
