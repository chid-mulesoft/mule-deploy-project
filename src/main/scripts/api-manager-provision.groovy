import groovy.json.JsonSlurper

class CrowdCICDUtil
{

	static def extractExchangeDetail()
	{
		println "START extractExchangeDetail "

		def inputFile = new File("crowd/classes/exchange.json")
		def exchangeDetail = new JsonSlurper().parseText(inputFile.text)
		//exchangeDetail.each{ println it }
		def apiVersion=exchangeDetail.apiVersion
		def assetId=exchangeDetail.assetId
		def groupId=exchangeDetail.groupId
		def assetVersion=exchangeDetail.version


		println "apiVersion=" + apiVersion
		println "assetId=" + assetId
		println "groupId=" + groupId
		println "assetVersion=" + assetVersion
		println "END extractExchangeDetail "
	}
	
	static void main(String[] args) {
      extractExchangeDetail();
   	} 
}