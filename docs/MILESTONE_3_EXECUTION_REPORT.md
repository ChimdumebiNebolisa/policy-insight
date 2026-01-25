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
