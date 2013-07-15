package release

/**
 * @author elberry
 * Created: Tue Aug 09 23:25:18 PDT 2011
 */
class SvnReleasePluginConvention {
    String tags = 'tags'
	
	String user = null // user used for svn authentication
	String password = null // password used for svn authentication
	
	boolean useCredentials() {
		return user && password
	}
}