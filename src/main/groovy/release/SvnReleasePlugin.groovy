package release

import java.util.regex.Matcher
import org.gradle.api.GradleException

/**
 * A command-line style SVN client. Requires user has SVN installed locally.
 * @author elberry
 * @author evgenyg
 * Created: Tue Aug 09 23:25:18 PDT 2011
 */
// TODO: Use SVNKit or SubversionJ
class SvnReleasePlugin extends BaseScmPlugin<SvnReleasePluginConvention> {

	private static final String ERROR = 'Commit failed'
	private static final def urlPattern = ~/URL:\s(.*?)(\/(trunk|branches|tags).*?)$/
	private static final def revPattern = ~/Revision:\s(.*?)$/

	void init() {
		findSvnUrl()
	}


	@Override
	SvnReleasePluginConvention buildConventionInstance() { new SvnReleasePluginConvention() }


	@Override
	void checkCommitNeeded() {
		String out = exec('svn', 'status')
		def changes = []
		def unknown = []
		out.eachLine { line ->
			switch (line?.trim()?.charAt(0)) {
				case '?':
					unknown << line
					break
				default:
					changes << line
					break
			}
		}
		if (changes) {
			warnOrThrow(releaseConvention().failOnCommitNeeded, "You have ${changes.size()} un-commited changes.")
		}
		if (unknown) {
			warnOrThrow(releaseConvention().failOnUnversionedFiles, "You have ${unknown.size()} un-versioned files.")
		}
	}


	@Override
	void checkUpdateNeeded() {
		def props = project.properties
		String svnUrl = props.releaseSvnUrl
		String svnRev = props.releaseSvnRev
		String svnRoot = props.releaseSvnRoot
		String svnRemoteRev = ""

		// svn status -q -u
		String out
		if (releaseConvention().useCredentials()) {
			log.info(">>> Using SVN password from plugin convention!")
			out = exec('svn', 'status', '-q', '-u', '--non-interactive', '--no-auth-cache', '--username', releaseConvention().svnUser, '--password', releaseConvention().svnPassword)
		} else {
			out = exec('svn', 'status', '-q', '-u')
		}

		def missing = []
		out.eachLine { line ->
			switch (line?.trim()?.charAt(0)) {
				case '*':
					missing << line
					break
			}
		}
		if (missing) {
			warnOrThrow(releaseConvention().failOnUpdateNeeded, "You are missing ${missing.size()} changes.")
		}

		if (releaseConvention().useCredentials()) {
			log.info(" >>> Using SVN password from plugin convention!")
			out = exec(true, [LC_COLLATE: "C", LC_CTYPE: "en_US.UTF-8"], 'svn', 'info', project.ext['releaseSvnUrl'], '--non-interactive', '--no-auth-cache', '--username', releaseConvention().svnUser, '--password', releaseConvention().svnPassword)
		} else {
			out = exec(true, [LC_COLLATE: "C", LC_CTYPE: "en_US.UTF-8"], 'svn', 'info', project.ext['releaseSvnUrl'])
		}

		out.eachLine { line ->
			Matcher matcher = line =~ revPattern
			if (matcher.matches()) {
				svnRemoteRev = matcher.group(1)
				project.ext.set('releaseRemoteSvnRev', svnRemoteRev)
			}
		}
		if (svnRev != svnRemoteRev) {
			// warn that there's a difference in local revision versus remote
			warnOrThrow(releaseConvention().failOnUpdateNeeded, "Local revision (${svnRev}) does not match remote (${svnRemoteRev}), local revision is used in tag creation.")
		}
	}


	@Override
	void createReleaseTag(String message = "") {
		def props = project.properties
		String svnUrl = props.releaseSvnUrl
		String svnRev = props.releaseSvnRev
		String svnRoot = props.releaseSvnRoot
		String svnTag = tagName()

		if (releaseConvention().useCredentials()) {
			log.info(" >>> Using SVN password from plugin convention!")
			exec('svn', 'cp', "${svnUrl}@${svnRev}", "${svnRoot}/tags/${svnTag}", '-m', message ?: "Created by Release Plugin: ${svnTag}", 
				'--non-interactive', '--no-auth-cache', '--username', releaseConvention().svnUser, '--password', releaseConvention().svnPassword)
		} else {
			exec('svn', 'cp', "${svnUrl}@${svnRev}", "${svnRoot}/tags/${svnTag}", '-m', message ?: "Created by Release Plugin: ${svnTag}")

		}
	}


	@Override
	void commit(String message) {
		if (releaseConvention().useCredentials()) {
			log.info(" >>> Using SVN password from plugin convention!")
			exec(['svn', 'ci', '-m', message, '--non-interactive', '--no-auth-cache', '--username', releaseConvention().svnUser, '--password', releaseConvention().svnPassword], 'Error committing new version', ERROR)
		} else {
			exec(['svn', 'ci', '-m', message], 'Error committing new version', ERROR)
		}
		
	}

	@Override
	void revert() {
		exec(['svn', 'revert', findPropertiesFile().name], 'Error reverting changes made by the release plugin.', ERROR)
	}



	private void findSvnUrl() {
		String out = exec(true, [LC_COLLATE: "C", LC_CTYPE: "en_US.UTF-8"], 'svn', 'info')

		out.eachLine { line ->
			Matcher matcher = line =~ urlPattern
			if (matcher.matches()) {
				String svnRoot = matcher.group(1)
				String svnProject = matcher.group(2)
				project.ext.set('releaseSvnRoot', svnRoot)
				project.ext.set('releaseSvnUrl', "$svnRoot$svnProject")
			}
			matcher = line =~ revPattern
			if (matcher.matches()) {
				String revision = matcher.group(1)
				project.ext.set('releaseSvnRev', revision)
			}
		}
		if (!project.hasProperty('releaseSvnUrl')) {
			throw new GradleException('Could not determine root SVN url.')
		}
	}
}