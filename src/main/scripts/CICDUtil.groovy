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

	static def String tmpFolderName="tmp";


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

		println ("Task: " + System.properties.'targetTask' )

		if (System.properties.'targetTask' == 'PrepareForBuild')
		{
			util.doPrepareForBuild(args)
		}

		if (System.properties.'targetTask' == 'PrepareForDeploy')
		{
			util.doPrepareForDeploy(args)
		}

   	} 

   	public void doPrepareForDeploy(args)
   	{
   		def targetOutputFile = getTargetOutputFile(System.properties.'targetOutputFile')

   		File targetDeployFile = invokeFindTargetDeployFile(System.properties.'targetDeployFileFolder', System.properties.'targetDeployFileNamePattern', true)

		targetOutputFile.append("mule.artefact.fileName="+targetDeployFile.absolutePath+"\n")

		invokeParsePOM(args, targetOutputFile)

		invokeExtractExchangeFile(args, targetOutputFile)
   	}


   	public void doPrepareForBuild(args)
   	{
   		def targetOutputFile = getTargetOutputFile(System.properties.'targetOutputFile')

   		File targetDeployFile = invokeFindTargetDeployFile(System.properties.'targetDeployFileFolder' + File.separator + 'target', System.properties.'targetDeployFileNamePattern', false)

		targetOutputFile.append("mule.artefact.fileName="+targetDeployFile.absolutePath+"\n")

		invokeParsePOM(args, targetOutputFile)

   	}

   	public File invokeFindTargetDeployFile(String targetDeployFileFolder, String targetDeployFileNamePattern, Boolean unzipTargetDeployFile)
   	{
   		def targetDeployFileName = new FileNameFinder().getFileNames(targetDeployFileFolder, targetDeployFileNamePattern)

		assert (targetDeployFileName != null): "target deploy file: $targetDeployFileName is missing"

		def targetDeployFile = new File(targetDeployFileName[0])

		assert targetDeployFile.canRead() : "file: $targetDeployFileName cannot be read"

		if (unzipTargetDeployFile)
		{
			def tmpFolder = System.properties.'targetDeployFileFolder' + File.separator + tmpFolderName

			def ant = new AntBuilder()

			ant.unzip(  src:targetDeployFile, dest: tmpFolder, overwrite:"false")
		}

		log (DEBUG, "file $targetDeployFileName is readable")

		return targetDeployFile
   	}

   	public void invokeExtractExchangeFile(String[] args, File targetOutputFile)
   	{

		def tmpFolder = System.properties.'targetDeployFileFolder' + File.separator + tmpFolderName

		def exchangeFileName = new FileNameFinder().getFileNames(tmpFolder, '**/exchange.json')

		assert (exchangeFileName.size() > 0): "Exchange file is missing"

		def ant = new AntBuilder()

		ant.copy(file: exchangeFileName[0], tofile: System.properties.'targetDeployFileFolder'+ File.separator + 'exchange.json')

		//handle mule-artifact.json file

		def artifactFiles = new FileNameFinder().getFileNames(tmpFolder, 'mule-artifact.json')


		if (artifactFiles.size() == 0 )
		{
			def artifactFile = new File('mule-artifact.json')
			artifactFile.append ('{}');
		}
		else
		{
			ant.copy(file: artifactFiles[0], tofile: System.properties.'targetDeployFileFolder'+ File.separator + '..' + File.separator + "mdp" + File.separator )
		}

   	}

   	public File getTargetOutputFile(String fileName)
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

	public void invokeParsePOM(String[] args, File targetOutputFile)
	{
		//ensure target output file is writtable, or create if not exist

		def groupId=System.properties.'targetDeployFile.groupId'
		def artifactId=System.properties.'targetDeployFile.artifactId'
		def version=System.properties.'targetDeployFile.version'
		def mulePackageType=System.properties.'targetDeployFile.mulePackageType'
		def muleRuntimeVersion=System.properties.'targetDeployFile.muleRuntimeVersion'


		// check the parsed in pom file readable

		def pomFileName = new FileNameFinder().getFileNames(System.properties.'targetDeployFileFolder', '*pom*')

		if (pomFileName != null && pomFileName[0] != null )
		{

			def pomFile = new File(pomFileName[0])

			assert pomFile.canRead() : "file: $pomFileName cannot be read"

			log(DEBUG, "file $pomFileName is readable")

			def pom = new XmlSlurper().parse(pomFile)

			//groupId
			groupId = pom.groupId.toString()

			//artifactId
			artifactId = pom.artifactId.toString()

			//version number
			version = pom.version.toString()

			//mule runtime version
			muleRuntimeVersion = pom.properties.'mule.version'

			//mule packaging type
			mulePackageType = "jar"

			if (muleRuntimeVersion =~ '3.*')
			{
				mulePackageType="zip"
			}
		}
		def muleTargetRepo = System.properties."repo.release.name"
		def muleTargetRepoUrl = System.properties."repo.release.url"


		if ( version =~ '.*SNAPSHOT') 
		{
			muleTargetRepo = System.properties."repo.snapshot.name"
		  	muleTargetRepoUrl = System.properties."repo.snapshot.url"
		}

		log(DEBUG, "POM group=$groupId, artifactId=$artifactId, version=$version mule.runtime.version=$muleRuntimeVersion, muleTargetRepo=$muleTargetRepo, muleTargetRepoUrl=$muleTargetRepoUrl")

		targetOutputFile.append("mule.artefact.group="+groupId+"\n")
		targetOutputFile.append("mule.artefact.artifactId="+artifactId+"\n")
		targetOutputFile.append("mule.artefact.version="+version+"\n")
		targetOutputFile.append("mule.artefact.packageType="+mulePackageType+"\n")
		targetOutputFile.append("mule.runtime.version="+muleRuntimeVersion+"\n")
		targetOutputFile.append("mule.artecfact.targetRepo="+muleTargetRepo+"\n")
		targetOutputFile.append("mule.artecfact.targetRepoUrl="+muleTargetRepoUrl+"\n")


	}
}