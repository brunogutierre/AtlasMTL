-- Answers replayed at no model cost when the same question comes back. The ingest job empties it
-- whenever a dataset changes, so a replay never contradicts the current data.
create table answer_cache (
  question_key  text primary key,          -- the question in lower case, without accents or extra spaces
  question      text        not null,
  events        jsonb       not null,      -- the AgentEvents of the original answer, in order
  created_at    timestamptz not null default now(),
  hits          integer     not null default 0
);
