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
	

	static void main(String[] args) {


		CICDUtil util = new CICDUtil();

		println ("Task: " + System.properties.'task' )


		if (System.properties.'task' == 'ParsePOM')
		{
			invokeParsePOM(args)
		}


   	} 

   	public static File getTargetOutputFile(String fileName)
   	{
		assert (fileName != null): "file name is missing"

		def file = new File(fileName)

		if (file.exists())
		{
			assert file.canWrite() : "file: $file cannot be written"
			log (DEBUG, "file $fileName is writeable")
		}
		else
		{
			file.write("");
		}
		return file
   	}

	public static void invokeParsePOM(String[] args)
	{
		//ensure target output file is writtable, or create if not exist

		def targetOutputFile

		if (System.properties.'targetOutputFile' != null )
		{
			targetOutputFile = getTargetOutputFile(System.properties.'targetOutputFile')
		}


		// check the parsed in pom file readable

		def pomFileName = System.properties.'pomFileName'

		assert (pomFileName != null): "pom file name is missing"

		def pomFile = new File(pomFileName)

		assert pomFile.canRead() : "file: $pomFileName cannot be read"

		log(DEBUG, "file $pomFileName is readable")

		def pom = new XmlSlurper().parse(pomFile)

		//groupId
		def groupId = pom.groupId.toString()

		//artifactId
		def artifactId = pom.artifactId.toString()

		//version number
		def version = pom.version.toString()

		log(DEBUG, "POM group=$groupId, artifactId=$artifactId, version=$version")

		targetOutputFile.append("group="+groupId+"\n")
		targetOutputFile.append("artifactId="+artifactId+"\n")
		targetOutputFile.append("version="+version+"\n")

	}
}