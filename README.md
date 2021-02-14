
## Build from source

### Initialize the project
```
./mvnw initialize
```

### Compile and package

```
./mvnw clean package
```

## Build Docker Image

docker build -t bloxbean/oan-dashboard-api:0.36 .

## Push to docker registry

docker push bloxbean/oan-dashboard-api:0.36


## Run

### Dependencies
- Redis : Redis is used as persistent store. 
- Get an api key from cryptocompare.com.

### Start the api server

- Open run.sh
- Enter appropriate values for redis_uri, batch_node, web3rpc_url, rate_api_url

- Make sure the jar file name & version are correct.

```aidl
$> ./run.sh
```

### Run in Kubernetes Cluster
Refer to kubernetes scripts under "deploy" folder.


