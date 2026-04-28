alter table policy_jobs add column demo_key varchar(80);

create unique index uk_policy_jobs_demo_key on policy_jobs(demo_key);
