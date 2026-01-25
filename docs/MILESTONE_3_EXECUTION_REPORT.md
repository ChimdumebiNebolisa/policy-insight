# Milestone 3 Execution Report

Started: 2026-01-25T00:12:34.4919531-06:00

## Step 1: Authenticate and set project/region

Timestamp: 2026-01-25T00:12:34.4919531-06:00

Command:
```
gcloud auth login
```

Result (failure):
```
gcloud : The term 'gcloud' is not recognized as the name of a cmdlet, function, script file, or operable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
At line:1 char:1
+ gcloud auth login
+ ~~~~~~
    + CategoryInfo          : ObjectNotFound: (gcloud:String) [], CommandNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException
```

Diagnosis:
- Google Cloud SDK (`gcloud`) is not installed or not on PATH.

Fix plan:
- Install Google Cloud SDK and ensure `gcloud` is available on PATH, then re-run `gcloud auth login`.

Timestamp: 2026-01-25T00:14:53.5406246-06:00

Fix attempt:
```
winget install --id Google.CloudSDK -e --source winget
```

Result (incomplete / timed out):
```
Found Google Cloud SDK [Google.CloudSDK] Version 553.0.0
Downloading https://dl.google.com/dl/cloudsdk/channels/rapid/GoogleCloudSDKInstaller.exe
Successfully verified installer hash
Starting package install...
The installer will request to run as administrator. Expect a prompt.
```

Notes:
- The command timed out while the installer was running and the shell was terminated.

Timestamp: 2026-01-25T00:15:10.7228745-06:00

Verification (still failing):
```
gcloud --version
```

Result:
```
gcloud : The term 'gcloud' is not recognized as the name of a cmdlet, function, script file, or operable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
At line:1 char:1
+ gcloud --version
+ ~~~~~~
    + CategoryInfo          : ObjectNotFound: (gcloud:String) [], CommandNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException
```

Timestamp: 2026-01-25T00:15:25.2585930-06:00

Fix attempt (user scope install):
```
winget install --id Google.CloudSDK -e --source winget --scope user --accept-package-agreements --accept-source-agreements
```

Result (failure):
```
No applicable installer found; see logs for more details.
```

## Resume Phase 3 - gcloud setup (evidence)

Brief note: gcloud was previously missing from PATH; located at:
`C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd`

Command:
```
gcloud config configurations list
```

Output:
```
NAME            IS_ACTIVE  ACCOUNT                       PROJECT         COMPUTE_DEFAULT_ZONE  COMPUTE_DEFAULT_REGION
default         False      chimdumebinebolisa@gmail.com
policy-insight  True       chimdumebinebolisa@gmail.com  policy-insight
```

Command:
```
gcloud config get-value project
```

Output:
```
Your active configuration is: [policy-insight]
policy-insight
```

Command:
```
gcloud config set run/region us-central1
```

Output:
```
Updated property [run/region].
```

Command:
```
gcloud config get-value run/region
```

Output:
```
Your active configuration is: [policy-insight]
us-central1
```

Command:
```
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com sqladmin.googleapis.com secretmanager.googleapis.com storage.googleapis.com pubsub.googleapis.com
```

Output:
```
Operation "operations/acf.p2-828177954618-84c20e52-89aa-4d61-b53b-c89c5aa3fdf2" finished successfully.
```

Command:
```
gcloud auth list
```

Output:
```
       Credentialed Accounts
ACTIVE  ACCOUNT
*       chimdumebinebolisa@gmail.com

To set the active account, run:
    $ gcloud config set account `ACCOUNT`
```

Command:
```
gcloud projects describe $(gcloud config get-value project)
```

Output:
```
Your active configuration is: [policy-insight]
createTime: '2025-12-31T11:33:34.125722Z'
lifecycleState: ACTIVE
name: policy-insight
projectId: policy-insight
projectNumber: '828177954618'
```

## Resume Phase 3 - Cloud SQL, secrets, deploy, smoke tests

Timestamp: 2026-01-25T00:42:12.4783000-06:00

Step: Confirm project/region (gcloud config get-value)

Command (initial attempt):
```
gcloud config get-value project
```

Result (failure):
```
gcloud : The term 'gcloud' is not recognized as the name of a cmdlet, function, script file, or operable program.
Check the spelling of the name, or if a path was included, verify that the path is correct and try again.
At line:1 char:1
+ gcloud config get-value project
+ ~~~~~~
    + CategoryInfo          : ObjectNotFound: (gcloud:String) [], CommandNotFoundException
    + FullyQualifiedErrorId : CommandNotFoundException
```

Fix:
- Use the absolute gcloud path for this session.

Command (re-run):
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
& $GCLOUD config get-value project
```

Output:
```
Your active configuration is: [policy-insight]
policy-insight
```

Command (initial attempt):
```
& $GCLOUD config get-value run/region
```

Result (failure):
```
The expression after '&' in a pipeline element produced an object that was not valid. It must result in a command
name, a script block, or a CommandInfo object.
At line:1 char:3
+ & $GCLOUD config get-value run/region
+   ~~~~~~~
    + CategoryInfo          : InvalidOperation: (:) [], RuntimeException
    + FullyQualifiedErrorId : BadExpression
```

Fix:
- Re-set `$GCLOUD` in the same shell command, then re-run.

Command (re-run):
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
& $GCLOUD config get-value run/region
```

Output:
```
Your active configuration is: [policy-insight]
us-central1
```

Step: Define names used by all commands (from plan)

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$REGION = (& $GCLOUD config get-value run/region).Trim()
$SERVICE = "policy-insight"
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
$DB_USER = "policyinsight"
$BUCKET = "$PROJECT_ID-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
```

Resolved values:
```
PROJECT_ID=policy-insight
REGION=us-central1
SERVICE=policy-insight
SQL_INSTANCE=policy-insight-db
DB_NAME=policyinsight
DB_USER=policyinsight
BUCKET=policy-insight-policyinsight
RUNTIME_SA_NAME=policy-insight-runner
RUNTIME_SA=policy-insight-runner@policy-insight.iam.gserviceaccount.com
```

## Step 4: Create Cloud SQL (Postgres 15) instance, database, and user

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$REGION = (& $GCLOUD config get-value run/region).Trim()
$SQL_INSTANCE = "policy-insight-db"
& $GCLOUD sql instances create $SQL_INSTANCE --database-version=POSTGRES_15 --tier=db-custom-1-3840 --region $REGION --project $PROJECT_ID
```

Output:
```
Your active configuration is: [policy-insight]
Your active configuration is: [policy-insight]
Creating Cloud SQL instance for POSTGRES_15...
.............done.
Created [https://sqladmin.googleapis.com/sql/v1beta4/projects/policy-insight/instances/policy-insight-db].
NAME               DATABASE_VERSION  LOCATION       TIER              PRIMARY_ADDRESS  PRIVATE_ADDRESS  STATUS
policy-insight-db  POSTGRES_15       us-central1-c  db-custom-1-3840  34.9.56.204      -                RUNNABLE
```

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
& $GCLOUD sql databases create $DB_NAME --instance $SQL_INSTANCE --project $PROJECT_ID
```

Output:
```
Your active configuration is: [policy-insight]
Creating Cloud SQL database...
.done.
Created database [policyinsight].
instance: policy-insight-db
name: policyinsight
project: policy-insight
```

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$SQL_INSTANCE = "policy-insight-db"
$DB_USER = "policyinsight"
$DB_PASSWORD = python -c "import secrets,base64; print(base64.urlsafe_b64encode(secrets.token_bytes(24)).decode())"
& $GCLOUD sql users create $DB_USER --instance $SQL_INSTANCE --password $DB_PASSWORD --project $PROJECT_ID
```

Output:
```
Your active configuration is: [policy-insight]
Creating Cloud SQL user...
.done.
Created user [policyinsight].
```

Note:
- DB password generated and stored in memory only (not logged).

## Step 5: Create GCS bucket for uploads/reports

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$REGION = (& $GCLOUD config get-value run/region).Trim()
$BUCKET = "$PROJECT_ID-policyinsight"
& $GCLOUD storage buckets create "gs://$BUCKET" --location $REGION --project $PROJECT_ID --uniform-bucket-level-access
```

Output:
```
Your active configuration is: [policy-insight]
Your active configuration is: [policy-insight]
Creating gs://policy-insight-policyinsight/...
```

## Step 6: Create the Cloud Run runtime service account

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$RUNTIME_SA_NAME = "policy-insight-runner"
& $GCLOUD iam service-accounts create $RUNTIME_SA_NAME --project $PROJECT_ID
```

Output:
```
Your active configuration is: [policy-insight]
Created service account [policy-insight-runner].
```

## Step 7: Grant IAM roles to the runtime service account

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
& $GCLOUD projects add-iam-policy-binding $PROJECT_ID --member "serviceAccount:$RUNTIME_SA" --role roles/cloudsql.client
```

Output:
```
Your active configuration is: [policy-insight]
Updated IAM policy for project [policy-insight].
bindings:
- members:
  - serviceAccount:828177954618@cloudbuild.gserviceaccount.com
  role: roles/cloudbuild.builds.builder
- members:
  - serviceAccount:service-828177954618@gcp-sa-cloudbuild.iam.gserviceaccount.com
  role: roles/cloudbuild.serviceAgent
- members:
  - serviceAccount:policy-insight-runner@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/cloudsql.client
- members:
  - serviceAccount:service-828177954618@containerregistry.iam.gserviceaccount.com
  role: roles/containerregistry.ServiceAgent
- members:
  - serviceAccount:828177954618-compute@developer.gserviceaccount.com
  role: roles/editor
- members:
  - serviceAccount:service-828177954618@gcp-sa-pubsub.iam.gserviceaccount.com
  role: roles/iam.serviceAccountTokenCreator
- members:
  - user:chimdumebinebolisa@gmail.com
  role: roles/owner
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/pubsub.publisher
- members:
  - serviceAccount:service-828177954618@gcp-sa-pubsub.iam.gserviceaccount.com
  role: roles/pubsub.serviceAgent
- members:
  - serviceAccount:service-828177954618@serverless-robot-prod.iam.gserviceaccount.com
  role: roles/run.serviceAgent
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/secretmanager.secretAccessor
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/storage.objectCreator
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/storage.objectViewer
etag: BwZJMUAl_yU=
version: 1
```

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
& $GCLOUD projects add-iam-policy-binding $PROJECT_ID --member "serviceAccount:$RUNTIME_SA" --role roles/secretmanager.secretAccessor
```

Output:
```
Your active configuration is: [policy-insight]
Updated IAM policy for project [policy-insight].
bindings:
- members:
  - serviceAccount:828177954618@cloudbuild.gserviceaccount.com
  role: roles/cloudbuild.builds.builder
- members:
  - serviceAccount:service-828177954618@gcp-sa-cloudbuild.iam.gserviceaccount.com
  role: roles/cloudbuild.serviceAgent
- members:
  - serviceAccount:policy-insight-runner@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/cloudsql.client
- members:
  - serviceAccount:service-828177954618@containerregistry.iam.gserviceaccount.com
  role: roles/containerregistry.ServiceAgent
- members:
  - serviceAccount:828177954618-compute@developer.gserviceaccount.com
  role: roles/editor
- members:
  - serviceAccount:service-828177954618@gcp-sa-pubsub.iam.gserviceaccount.com
  role: roles/iam.serviceAccountTokenCreator
- members:
  - user:chimdumebinebolisa@gmail.com
  role: roles/owner
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/pubsub.publisher
- members:
  - serviceAccount:service-828177954618@gcp-sa-pubsub.iam.gserviceaccount.com
  role: roles/pubsub.serviceAgent
- members:
  - serviceAccount:service-828177954618@serverless-robot-prod.iam.gserviceaccount.com
  role: roles/run.serviceAgent
- members:
  - serviceAccount:policy-insight-runner@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/secretmanager.secretAccessor
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/storage.objectCreator
- members:
  - serviceAccount:policyinsight-web@policy-insight.iam.gserviceaccount.com
  - serviceAccount:policyinsight-worker@policy-insight.iam.gserviceaccount.com
  role: roles/storage.objectViewer
etag: BwZJMUDYIWs=
version: 1
```

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$BUCKET = "$PROJECT_ID-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
& $GCLOUD storage buckets add-iam-policy-binding "gs://$BUCKET" --member "serviceAccount:$RUNTIME_SA" --role roles/storage.objectAdmin
```

Output:
```
Your active configuration is: [policy-insight]
bindings:
- members:
  - projectEditor:policy-insight
  - projectOwner:policy-insight
  role: roles/storage.legacyBucketOwner
- members:
  - projectViewer:policy-insight
  role: roles/storage.legacyBucketReader
- members:
  - projectEditor:policy-insight
  - projectOwner:policy-insight
  role: roles/storage.legacyObjectOwner
- members:
  - projectViewer:policy-insight
  role: roles/storage.legacyObjectReader
- members:
  - serviceAccount:policy-insight-runner@policy-insight.iam.gserviceaccount.com
  role: roles/storage.objectAdmin
etag: CAI=
kind: storage#policy
resourceId: projects/_/buckets/policy-insight-policyinsight
version: 1
```

## Step 8: Create secrets in Secret Manager

Command (reset DB password and create secrets):
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$SQL_INSTANCE = "policy-insight-db"
$DB_USER = "policyinsight"
$DB_PASSWORD = python -c "import secrets,base64; print(base64.urlsafe_b64encode(secrets.token_bytes(24)).decode())"
& $GCLOUD sql users set-password $DB_USER --instance $SQL_INSTANCE --password $DB_PASSWORD --project $PROJECT_ID
& $GCLOUD secrets create db-password --replication-policy=automatic --project $PROJECT_ID
$DB_PASSWORD | & $GCLOUD secrets versions add db-password --data-file=- --project $PROJECT_ID
$APP_TOKEN_SECRET = python -c "import secrets,base64; print(base64.urlsafe_b64encode(secrets.token_bytes(32)).decode())"
& $GCLOUD secrets create app-token-secret --replication-policy=automatic --project $PROJECT_ID
$APP_TOKEN_SECRET | & $GCLOUD secrets versions add app-token-secret --data-file=- --project $PROJECT_ID
```

Output:
```
Your active configuration is: [policy-insight]
Updating Cloud SQL user...
.done.
Created secret [db-password].
Created version [1] of the secret [db-password].
Created secret [app-token-secret].
Created version [1] of the secret [app-token-secret].
```

Note:
- Secret values generated and stored; not printed.

## Step 9: Deploy Cloud Run from source (initial attempt from plan)

Command (from plan):
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$REGION = (& $GCLOUD config get-value run/region).Trim()
$SERVICE = "policy-insight"
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
$DB_USER = "policyinsight"
$BUCKET = "$PROJECT_ID-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
$CONNECTION_NAME = (& $GCLOUD sql instances describe $SQL_INSTANCE --project $PROJECT_ID --format="value(connectionName)").Trim()
& $GCLOUD run deploy $SERVICE --source . --region $REGION --project $PROJECT_ID --service-account $RUNTIME_SA --add-cloudsql-instances $CONNECTION_NAME --min-instances 0 --allow-unauthenticated --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,DB_HOST=/cloudsql/$CONNECTION_NAME,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000" --set-secrets "DB_PASSWORD=db-password:latest,APP_TOKEN_SECRET=app-token-secret:latest"
```

Output (failure):
```
Deploying from source requires an Artifact Registry Docker repository to store
built containers. A repository named [cloud-run-source-deploy] in region
[us-central1] will be created.

Do you want to continue (Y/n)?
Building using Dockerfile and deploying container to Cloud Run service [policy-insight] in project [policy-insight] region [us-central1]
Building and deploying new service...
Validating configuration......done
Creating Container Repository............................................................................................................................................................................done
Uploading sources..............................done
Building Container...........................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................................done
Setting IAM Policy...........done
Creating Revision..................................................................................................................................................................................................failed
Deployment failed
ERROR: (gcloud.run.deploy) The user-provided container failed to start and listen on the port defined provided by the PORT=8080 environment variable within the allocated timeout. This can happen when the container port is misconfigured or if the timeout is too short. The health check timeout can be extended. Logs for this revision might contain more information.
```

Diagnosis:
- Logs show JDBC URL invalid for Cloud SQL socket: `Driver org.postgresql.Driver claims to not accept jdbcUrl, jdbc:postgresql:///cloudsql/...:5432/policyinsight`.

Fix plan:
- Switch to Cloud SQL JDBC socket factory and set `SPRING_DATASOURCE_URL` explicitly.

## Step 9: Deploy attempt with DB_HOST set to public IP (diagnostic)

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$REGION = (& $GCLOUD config get-value run/region).Trim()
$SERVICE = "policy-insight"
$SQL_INSTANCE = "policy-insight-db"
$DB_NAME = "policyinsight"
$DB_USER = "policyinsight"
$DB_HOST = (& $GCLOUD sql instances describe $SQL_INSTANCE --project $PROJECT_ID --format="value(ipAddresses[0].ipAddress)").Trim()
$BUCKET = "$PROJECT_ID-policyinsight"
$RUNTIME_SA_NAME = "policy-insight-runner"
$RUNTIME_SA = "$RUNTIME_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
$CONNECTION_NAME = (& $GCLOUD sql instances describe $SQL_INSTANCE --project $PROJECT_ID --format="value(connectionName)").Trim()
& $GCLOUD run deploy $SERVICE --source . --region $REGION --project $PROJECT_ID --service-account $RUNTIME_SA --add-cloudsql-instances $CONNECTION_NAME --min-instances 0 --allow-unauthenticated --set-env-vars "SPRING_PROFILES_ACTIVE=cloudrun,DB_HOST=$DB_HOST,DB_PORT=5432,DB_NAME=$DB_NAME,DB_USER=$DB_USER,APP_STORAGE_MODE=gcp,GCS_BUCKET_NAME=$BUCKET,APP_MESSAGING_MODE=local,APP_PROCESSING_MODE=local,POLICYINSIGHT_WORKER_ENABLED=true,APP_RATE_LIMIT_UPLOAD_MAX_PER_HOUR=10,APP_RATE_LIMIT_QA_MAX_PER_HOUR=20,APP_RATE_LIMIT_QA_MAX_PER_JOB=3,APP_PROCESSING_MAX_TEXT_LENGTH=1000000,APP_PROCESSING_STAGE_TIMEOUT_SECONDS=300,APP_VALIDATION_PDF_MAX_PAGES=100,APP_VALIDATION_PDF_MAX_TEXT_LENGTH=1048576,APP_RETENTION_DAYS=30,APP_LOCAL_WORKER_POLL_MS=2000,APP_LOCAL_WORKER_BATCH_SIZE=5,APP_JOB_LEASE_DURATION_MINUTES=30,APP_JOB_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_MAX_ATTEMPTS=3,APP_GEMINI_RETRY_BASE_DELAY_MS=1000" --set-secrets "DB_PASSWORD=db-password:latest,APP_TOKEN_SECRET=app-token-secret:latest"
```

Output (failure):
```
Deployment failed
ERROR: (gcloud.run.deploy) The user-provided container failed to start and listen on the port defined provided by the PORT=8080 environment variable within the allocated timeout. This can happen when the container port is misconfigured or if the timeout is too short. The health check timeout can be extended. Logs for this revision might contain more information.
```

Diagnosis:
- Logs show `java.net.SocketTimeoutException: Connect timed out` when connecting to public IP.

## Step 9: Deploy attempt with Cloud SQL socket factory (build failure)

Change:
- Added dependency `com.google.cloud.sql:postgres-socket-factory` to `pom.xml` to support Cloud SQL JDBC socket factory.

Command (gcloud.cmd):
```
$SPRING_DATASOURCE_URL = "jdbc:postgresql:///$DB_NAME?cloudSqlInstance=$CONNECTION_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
& $GCLOUD run deploy ... --set-env-vars "SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,..."
```

Output (failure):
```
Deployment failed
ERROR: (gcloud.run.deploy) Build failed; check build logs for details
'socketFactory' is not recognized as an internal or external command,
operable program or batch file.
```

Fix:
- Switch to `gcloud.ps1` to avoid `gcloud.cmd` parsing of `&`.

## Step 9: Deploy attempt with gcloud.ps1 (build failure)

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.ps1"
... (same deploy command)
```

Output (failure):
```
Deployment failed
ERROR: (gcloud.run.deploy) Build failed; check build logs for details
```

Build log diagnosis:
```
[ERROR] 'dependencies.dependency.version' for com.google.cloud.sql:postgres-socket-factory:jar is missing. @ line 92, column 21
```

Fix:
- Pinned dependency version to `1.15.2` in `pom.xml`.

## Step 9: Deploy attempt with socket factory (runtime failure)

Command:
```
$SPRING_DATASOURCE_URL = "jdbc:postgresql:///$DB_NAME?cloudSqlInstance=$CONNECTION_NAME&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
& $GCLOUD run deploy ... --set-env-vars "SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,..."
```

Output (failure):
```
Deployment failed
ERROR: (gcloud.run.deploy) The user-provided container failed to start and listen on the port defined provided by the PORT=8080 environment variable within the allocated timeout.
```

Diagnosis:
- Logs show `SPRING_DATASOURCE_URL` interpolated incorrectly due to PowerShell variable parsing (`$DB_NAME?cloudSqlInstance` treated as a variable), resulting in `jdbc:postgresql:///=policy-insight:us-central1:policy-insight-db...` and connection errors.

Fix:
- Use `${DB_NAME}` and `${CONNECTION_NAME}` interpolation to preserve `?cloudSqlInstance` in the URL.

## Step 9: Deploy attempt with corrected JDBC URL (runtime failure)

Command:
```
$SPRING_DATASOURCE_URL = "jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
& $GCLOUD run deploy ... --set-env-vars "SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,..."
```

Output (failure):
```
Deployment failed
ERROR: (gcloud.run.deploy) The user-provided container failed to start and listen on the port defined provided by the PORT=8080 environment variable within the allocated timeout.
```

Diagnosis:
- Logs show `SPRING_DATASOURCE_URL` correct but Postgres SCRAM parsing fails:
  - `Caused by: java.lang.IllegalArgumentException: Prohibited character`

Fix:
- Reset DB password to alphanumeric-only and update Secret Manager.

Command (reset DB password and update secret):
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.ps1"
$PROJECT_ID = (& $GCLOUD config get-value project).Trim()
$SQL_INSTANCE = "policy-insight-db"
$DB_USER = "policyinsight"
$DB_PASSWORD = -join ((48..57 + 65..90 + 97..122) | Get-Random -Count 32 | ForEach-Object { [char]$_ })
& $GCLOUD sql users set-password $DB_USER --instance $SQL_INSTANCE --password $DB_PASSWORD --project $PROJECT_ID
$DB_PASSWORD | & $GCLOUD secrets versions add db-password --data-file=- --project $PROJECT_ID
```

Output:
```
Your active configuration is: [policy-insight]
Updating Cloud SQL user...
.done.
Created version [2] of the secret [db-password].
```

## Step 9: Deploy attempt after DB password reset (in progress)

Command:
```
$GCLOUD = "C:\Users\Chimdumebi\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.ps1"
$SPRING_DATASOURCE_URL = "jdbc:postgresql:///${DB_NAME}?cloudSqlInstance=${CONNECTION_NAME}&socketFactory=com.google.cloud.sql.postgres.SocketFactory"
& $GCLOUD run deploy ... --set-env-vars "SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL,..."
```

Output (aborted):
```
Building using Dockerfile and deploying container to Cloud Run service [policy-insight] in project [policy-insight] region [us-central1]
Building and deploying...
Validating configuration......done
Uploading sources.......................done
```

Note:
- Deployment command was aborted twice while uploading sources; rerun required to complete deploy.

## Step 9: Deploy attempt (retry, still aborted)

Output (aborted):
```
Building using Dockerfile and deploying container to Cloud Run service [policy-insight] in project [policy-insight] region [us-central1]
Building and deploying...
Validating configuration.......done
Uploading sources......................done
```

Note:
- Deployment command aborted again during upload; requires uninterrupted run to finish.
