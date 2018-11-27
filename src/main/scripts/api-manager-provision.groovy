import groovy.json.JsonSlurper
import groovy.json.JsonOutput 
import groovyx.net.http.*
import groovyx.net.http.ContentType.*
import groovyx.net.http.Method.*


class CICDUtil
{
	static def int WARN=1;
	static def int INFO=2;
	static def int DEBUG=3;
	static def int TRACE=4;



	static def logLevel = DEBUG;  //root logger level

	static def log (java.lang.Integer level, java.lang.Object content)
	{
		if (level <= logLevel)
		{
			def logPrefix = new Date().format("YYYYMMdd-HH:mm:ss") 
			if (level == WARN)
			{
				logPrefix += " WARN"
			}
			if (level == INFO)
			{
				logPrefix += " INFO"
			}
			if (level == DEBUG)
			{
				logPrefix += " DEBUG"
			}
			if (level == TRACE)
			{
				logPrefix += " TRACE"
			}
			println logPrefix + " : " + content 
		}

	}
	def extractExchangeAssetDetail(props)
	{
		log(DEBUG,  "START extractExchangeAssetDetail")
 
		def inputFile = new File(props.exchangeFileName)

		assert inputFile.exists() : "exchange.json file not found"
		assert inputFile.canRead() : "exchange.json file cannot be read"

		if (inputFile.length() == 0)
		{
			log(DEBUG, "input exchange.json file is empty, going to use the exchange.json file packaged in the archive")
		}

		def exchangeDetail = new JsonSlurper().parseText(inputFile.text)


		def apiVersion=exchangeDetail.apiVersion
		def assetId=exchangeDetail.assetId
		def groupId=exchangeDetail.groupId
		def assetVersion=exchangeDetail.version

		exchangeDetail.each{ log(DEBUG, it) }
		
		log(DEBUG,  "END extractExchangeAssetDetail")

		return exchangeDetail;
	}

	def getAnypointToken(props)
	{
		log(DEBUG,  "START getAnypointToken")


		def username=props.username
		def password=props.password 


		log(TRACE, "username=" + username)
		log(TRACE, "password=" + password)

		def urlString = "https://anypoint.mulesoft.com/accounts/login"

		def message = 'username='+username+'&password='+password

		def headers=["Content-Type":"application/x-www-form-urlencoded", "Accept": "application/json"]

		def connection = doRESTHTTPCall(urlString, "POST", message, headers)

		if ( connection.responseCode =~ '2..') 
		{

		}else
		{
			throw new Exception("Failed to get the login token!")
		}

		def response = "${connection.content}"

		def token = new JsonSlurper().parseText(response).access_token

		log(INFO, "Bearer Token: ${token}")

		log(DEBUG,  "END getAnypointToken")

		return token

	}
	
	def init ()
	{
		def props = ['username':System.properties.'anypoint.user', 
					 'password': System.properties.'anypoint.password',
					 'exchangeFileName': System.properties.'exchangeFileName',
					 'orgId': System.properties.'orgId',
					 'envId': System.properties.'envId',
                                         'isMule4OrAbove': System.properties.'isMule4OrAbove'.equalsIgnoreCase( 'true' ),
					 'targetPropFile' : System.properties.'targetPropFile'
					]

		log(DEBUG,  "props->" + props)
		return props;
	}


	def provisionAPIManager(props, exchangeDetail)
	{
		def token = getAnypointToken(props);

		def result = getAPIInstanceByExchangeAssetDetail(props, exchangeDetail, token);

		log(INFO, "apiInstance=" + result)

		return result
	}

	def getAPIInstanceByExchangeAssetDetail(props, exchangeDetail, token)
	{

		log(DEBUG,  "START getAPIInstanceByExchangeAssetDetail")

		def apiInstance
		def apiDiscoveryName
		def apiDiscoveryVersion
		def apiDiscoveryId

		def urlString = "https://anypoint.mulesoft.com/exchange/api/v1/assets/"+props.orgId+"/"+exchangeDetail.assetId

		def headers=["Content-Type":"application/json", "Authorization": "Bearer " + token, "Accept": "application/json"]

		def connection = doRESTHTTPCall(urlString, "GET", null, headers)

		if (connection.responseCode == 404)
		{
			log(INFO, "API Instance for " + exchangeDetail.assetId + " is not found in API Manager")

		} 
		else if (connection.responseCode == 200)
		{
			log(INFO, "API Instances for " + exchangeDetail.assetId + " has been found in the platform ");

			def response = "${connection.content}"

			def allAPIInstances = new JsonSlurper().parseText(response).instances;

			allAPIInstances.each{ 
				log(INFO, it)
				if (it.environmentId == props.envId && it.version == exchangeDetail.version)
				{
					apiInstance = it;
					apiDiscoveryName = "groupId:"+props.orgId+":assetId:"+ exchangeDetail.assetId
					apiDiscoveryVersion = apiInstance.name
					apiDiscoveryId = apiInstance.id
				}
			}

			log(INFO, "apiInstance for env " + props.envId + " is " + apiInstance);

		}

		if (apiInstance == null)
		{
			apiInstance = createAPIInstance(token, exchangeDetail, props)
			apiDiscoveryName = "groupId:"+props.orgId+":assetId:"+ exchangeDetail.assetId
			apiDiscoveryVersion = apiInstance.autodiscoveryInstanceName
			apiDiscoveryId = apiInstance.id

		}

		def result = ["apiInstance": apiInstance, "apiDiscoveryName": apiDiscoveryName, "apiDiscoveryVersion":apiDiscoveryVersion, "apiDiscoveryId": apiDiscoveryId]

		log(DEBUG,  "END getAPIInstanceByExchangeAssetDetail")

		return result

	}

	def createAPIInstance(token, exchangeDetail, props)
	{
		log(DEBUG,  "START createAPIInstance")

		def requestTemplate = '{ "endpoint": { "deploymentType": null, "isCloudHub": null, "muleVersion4OrAbove": null, "proxyUri": null, "referencesUserDomain": null, "responseTimeout": null, "type": null, "uri": null }, "instanceLabel": null, "spec": { "assetId": null, "groupId": null, "version": null } }'

		def request = new JsonSlurper().parseText(requestTemplate);

		request.endpoint.deploymentType = 'CH'
		request.endpoint.type='rest-api'
		request.endpoint.muleVersion4OrAbove = props.isMule4OrAbove
		request.spec.assetId=exchangeDetail.assetId
		request.spec.groupId=props.orgId
		request.spec.version=exchangeDetail.version


		def message = JsonOutput.toJson(request)
		log(INFO, "createAPIInstance request message=" + message);

		def urlString = "https://anypoint.mulesoft.com/apimanager/api/v1/organizations/"+props.orgId+"/environments/"+props.envId + "/apis"

		def headers=["Content-Type":"application/json", "Authorization": "Bearer " + token, "Accept": "application/json"]

		def connection = doRESTHTTPCall(urlString, "POST", message, headers)

		def response = "${connection.content}"

		if ( connection.responseCode =~ '2..') 
		{
			log(INFO, "the API instance is created successfully! statusCode=" + connection.responseCode)
		}
		else
		{
			throw new Exception("Failed to create API Instance! statusCode=${connection.responseCode} responseMessage=${response}")
		}
	

		def apiInstance = new JsonSlurper().parseText(response)

		log(DEBUG,  "END createAPIInstance")

		return apiInstance;
	}

	static def doRESTHTTPCall(urlString, method, payload, headers)
	{
		log(DEBUG,  "START doRESTHTTPCall")

		log(INFO, "requestURl is " + urlString)

		def url = new URL(urlString)

		def connection = url.openConnection()

		headers.keySet().each {
			log(INFO, it + "->" + headers.get(it))
			connection.setRequestProperty(it, headers.get(it))
		}

		connection.doOutput = true

		if (method == "POST")
		{
			connection.setRequestMethod("POST")
			def writer = new OutputStreamWriter(connection.outputStream)
			writer.write(payload)
			writer.flush()
			writer.close()
		}
		else if (method == "GET")
		{
			connection.setRequestMethod("GET")
		}

		
		connection.connect();
		

		log(DEBUG,  "END doRESTHTTPCall")

		return connection

	}

	def persisteAPIDiscoveryDetail (props, result)
	{
		def outputFile = new File(props.targetPropFile)

		assert outputFile.canWrite() : "${props.targetPropFile} file cannot be write"

		outputFile.append("apiDiscoveryVersion="+result.apiDiscoveryVersion+"\n")
		outputFile.append("apiDiscoveryName="+result.apiDiscoveryName+"\n")
		outputFile.append("apiDiscoveryId="+result.apiDiscoveryId+"\n")


	}

	static void main(String[] args) {


		CICDUtil util = new CICDUtil();

		def props = util.init();
      
      	def exchangeDetail = util.extractExchangeAssetDetail(props);

      	def result = util.provisionAPIManager(props, exchangeDetail);

      	util.persisteAPIDiscoveryDetail(props, result)


   	} 
}
