# mule-deploy-project

This project can be used as standalone deployment project for different deployment targets, this includes
  1. Anypoint Runtime Manager
  2. Cloudhub
  3. MMC
  4. API Manager
  
Different Maven profile can be use for the deployment target, for instance, to deploy the target to ARM, the maven command will
be  "mvn deploy -p arm"

## create API instance in API Manager 

The maven profile "crowd" is used to provision the API Manager for the API Instance. 

Once the API asset is published in Exchange, it will be created with a meta info file "exchange.json", it contains the asset's information in Exchange. This is file is essential in binding the API Spec (RAML Files in Exchange) with the API implementation (Mule app as kept in SVC). 

The maven command will be used to create or lookup an API instance in API Manager's dedicated environment.

mvn package -P crowd  -Danypoint.user=derek.lin -Danypoint.password=<ANYPOINT PASSWORD>  -DorgId=<ORG ID> -DenvId=<ENV ID>  -DexchangeFileName=<LOCATION OF THE exchange.json file> -DtargetPropFile=<OUTPUT FILE for holding the api discovery detail>
