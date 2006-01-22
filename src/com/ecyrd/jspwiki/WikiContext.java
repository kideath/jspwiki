/* 
    JSPWiki - a JSP-based WikiWiki clone.

    Copyright (C) 2001 Janne Jalkanen (Janne.Jalkanen@iki.fi)

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.ecyrd.jspwiki;

import java.security.Permission;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.PropertyPermission;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.PageContext;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import com.ecyrd.jspwiki.auth.AuthorizationManager;
import com.ecyrd.jspwiki.auth.WikiPrincipal;
import com.ecyrd.jspwiki.auth.authorize.DefaultGroupManager;
import com.ecyrd.jspwiki.auth.permissions.PagePermission;
import com.ecyrd.jspwiki.auth.permissions.WikiPermission;
import com.ecyrd.jspwiki.providers.ProviderException;
import com.ecyrd.jspwiki.tags.WikiTagBase;
import com.ecyrd.jspwiki.util.HttpUtil;

/**
 *  <p>Provides state information throughout the processing of a page.  A
 *  WikiContext is born when the JSP pages that are the main entry
 *  points, are invoked.  The JSPWiki engine creates the new
 *  WikiContext, which basically holds information about the page, the
 *  handling engine, and in which context (view, edit, etc) the
 *  call was done.</p>
 *  <p>A WikiContext also provides request-specific variables, which can
 *  be used to communicate between plugins on the same page, or
 *  between different instances of the same plugin.  A WikiContext
 *  variable is valid until the processing of the page has ended.  For
 *  an example, please see the Counter plugin.</p>
 *  <p>When a WikiContext is created, it automatically associates a 
 *  {@link WikiSession} object with the user's HttpSession. The
 *  WikiSession contains information about the user's authentication
 *  status, and is consulted by {@link #getCurrentUser()}.
 *  object</p>
 *
 *  @see com.ecyrd.jspwiki.plugin.Counter
 *  
 *  @author Janne Jalkanen
 *  @author Andrew R. Jaquith
 */
public class WikiContext
    implements Cloneable
{
    WikiPage   m_page;
    WikiPage   m_realPage;
    WikiEngine m_engine;
    Permission m_permission = null;
    String     m_requestContext = VIEW;
    String     m_template       = "default";

    Map        m_variableMap = new HashMap();

    HttpServletRequest m_request = null;

    WikiSession m_session = null;

    /** The VIEW context - the user just wants to view the page
        contents. */
    public static final String    VIEW     = "view";

    /** The EDIT context - the user is editing the page. */
    public static final String    EDIT     = "edit";

    /** User is preparing for a login/authentication. */
    public static final String    LOGIN    = "login";

    /** User is preparing to log out. */
    public static final String    LOGOUT   = "logout";

    /** User is viewing a DIFF between the two versions of the page. */
    public static final String    DIFF     = "diff";

    /** User is viewing page history. */
    public static final String    INFO     = "info";

    /** User is previewing the changes he just made. */
    public static final String    PREVIEW  = "preview";

    /** User has an internal conflict, and does quite not know what to
        do. Please provide some counseling. */
    public static final String    CONFLICT = "conflict";

    /** An error has been encountered and the user needs to be informed. */
    public static final String    ERROR    = "error";

    public static final String    UPLOAD   = "upload";

    public static final String    COMMENT  = "comment";
    public static final String    FIND     = "find";

    public static final String    CREATE_GROUP = "createGroup";
    public static final String    PREFS    = "prefs";
    
    /** Deprecated. @deprecated */
    public static final String    REGISTER = "register";
    
    public static final String    RENAME   = "rename";
    public static final String    DELETE   = "del";
    public static final String    ATTACH   = "att";
    public static final String    RSS      = "rss";

    public static final String    NONE     = "";  // This is not a JSPWiki context, use it to access static files
    
    public static final String    OTHER    = NONE; // This is just a clarification.
    
    protected static Logger       log      = Logger.getLogger( WikiContext.class );
    private static final Permission DUMMY_PERMISSION = new PropertyPermission("os.name", "read");

    /**
     *  Create a new WikiContext for the given WikiPage. Delegates to
     * {@link #WikiContext(WikiEngine, HttpServletRequest, WikiPage)}.
     *  @param engine The WikiEngine that is handling the request.
     *  @param page   The WikiPage.  If you want to create a
     *  WikiContext for an older version of a page, you must use this
     *  constructor. 
     */
    public WikiContext( WikiEngine engine, WikiPage page )
    {
        this(engine, null, page);
    }
    
    /**
     * <p>
     * Creates a new WikiContext for the given WikiEngine, WikiPage and
     * HttpServletRequest. This constructor will also look up the HttpSession
     * associated with the request, and determine if a WikiSession object is
     * present. If not, a new one is created.
     * </p>
     * <p>
     * After the WikiSession object is obtained, the current authentication
     * status is checked. If not authenticated, or if the login status reported
     * by the container has changed, the constructor attempts to log in the user
     * with
     * {@link com.ecyrd.jspwiki.auth.AuthenticationManager#login(HttpServletRequest)}.
     * </p>
     * @param engine The WikiEngine that is handling the request
     * @param request The HttpServletRequest that should be associated with this
     *            context. This parameter may be <code>null</code>.
     * @param page The WikiPage. If you want to create a WikiContext for an
     *            older version of a page, you must supply this parameter
     */
    public WikiContext(WikiEngine engine, HttpServletRequest request, WikiPage page) 
    {
        super();
        m_engine = engine;
        m_request = request;
        m_session = WikiSession.getWikiSession( request );
        m_page   = page;
        m_realPage = page;
        
        // Log in the user if new session or the container status changed
        boolean doLogin = ( (request != null) && m_session.getLastContext() == null );
        
        // Debugging...
        if ( log.isDebugEnabled() )
        {
            HttpSession session = ( request == null ) ? null : request.getSession( false );
            String sid = ( session == null ) ? "(null)" : session.getId();
            log.debug( "Creating WikiContext for session ID=" + sid + "; page=" + page.getName() );
            log.debug( "Do we need to log the user in? " + doLogin );
        }
        
        if ( doLogin || m_session.isContainerStatusChanged( request ) )
        {
            engine.getAuthenticationManager().login( request );
        }

        // Stash the wiki context in the session as the "last context"
        m_session.setLastContext( this );
        
        // Figure out what permission is required to execute this context
        updatePermission();
    }

    /**
     *  Sometimes you may want to render the page using some other page's context.
     *  In those cases, it is highly recommended that you set the setRealPage()
     *  to point at the real page you are rendering.
     *  
     *  @param page  The real page which is being rendered.
     *  @since 2.3.14
     *  @return The previous real page
     */
    public WikiPage setRealPage( WikiPage page )
    {
        WikiPage old = m_realPage;
        m_realPage = page;
        updatePermission();
        return old;
    }
    
    public WikiPage getRealPage()
    {
        return m_realPage;
    }
    
    /**
     *  Returns the handling engine.
     */
    public WikiEngine getEngine()
    {
        return m_engine;
    }

    /**
     *  Returns the page that is being handled.
     */
    public WikiPage getPage()
    {
        return m_page;
    }

    /**
     *  Sets the page that is being handled.
     *
     *  @since 2.1.37.
     */
    public void setPage( WikiPage page )
    {
        m_page = page;
        updatePermission();
    }

    /**
     *  Returns the request context.
     */
    public String getRequestContext()
    {
        return m_requestContext;
    }

    /**
     *  Sets the request context.  See above for the different
     *  request contexts (VIEW, EDIT, etc.)
     *
     *  @param arg The request context (one of the predefined contexts.)
     */
    public void setRequestContext( String arg )
    {
        m_requestContext = arg;
        updatePermission();
    }

    /**
     *  Gets a previously set variable.
     *
     *  @param key The variable name.
     *  @return The variable contents.
     */
    public Object getVariable( String key )
    {
        return m_variableMap.get( key );
    }

    /**
     *  Sets a variable.  The variable is valid while the WikiContext is valid,
     *  i.e. while page processing continues.  The variable data is discarded
     *  once the page processing is finished.
     *
     *  @param key The variable name.
     *  @param data The variable value.
     */
    public void setVariable( String key, Object data )
    {
        m_variableMap.put( key, data );
        updatePermission();
    }

    /**
     *  This method will safely return any HTTP parameters that 
     *  might have been defined.  You should use this method instead
     *  of peeking directly into the result of getHttpRequest(), since 
     *  this method is smart enough to do all of the right things,
     *  figure out UTF-8 encoded parameters, etc.
     *
     *  @since 2.0.13.
     *  @param paramName Parameter name to look for.
     *  @return HTTP parameter, or null, if no such parameter existed.
     */
    public String getHttpParameter( String paramName )
    {
        String result = null;

        if( m_request != null )
        {
            result = m_request.getParameter( paramName );
        }

        return result;
    }

    /**
     *  If the request did originate from a HTTP request,
     *  then the HTTP request can be fetched here.  However, it the request
     *  did NOT originate from a HTTP request, then this method will
     *  return null, and YOU SHOULD CHECK FOR IT!
     *
     *  @return Null, if no HTTP request was done.
     *  @since 2.0.13.
     */
    public HttpServletRequest getHttpRequest()
    {
        return m_request;
    }

    /**
     *  Sets the template to be used for this request.
     *  @since 2.1.15.
     */
    public void setTemplate( String dir )
    {
        m_template = dir;
    }

    /**
     *  Gets the template that is to be used throughout this request.
     *  @since 2.1.15.
     */
    public String getTemplate()
    {
        return m_template;
    }

    /**
     *  Convenience method that gets the current user. Delegates the
     *  lookup to the WikiSession associated with this WikiContect. 
     *  May return null, in case the current
     *  user has not yet been determined; or this is an internal system.
     *  If the WikiSession has not been set, <em>always</em> returns null.
     */
    public Principal getCurrentUser()
    {
        if (m_session == null) 
        {
            // This shouldn't happen, really...
            return WikiPrincipal.GUEST;
        }
        return m_session.getUserPrincipal();
    }

    public String getViewURL( String page )
    {
        return getURL( VIEW, page, null );
    }

    public String getURL( String context,
                          String page )
    {
        return getURL( context, page, null );
    }

    /**
     *  Returns an URL from a page. It this WikiContext instance was constructed
     *  with an actual HttpServletRequest, we will attempt to construct the
     *  URL using HttpUtil, which preserves the HTTPS portion if it was used.
     */
    public String getURL( String context,
                          String page,
                          String params )
    {
        boolean absolute = "absolute".equals(m_engine.getVariable( this, WikiEngine.PROP_REFSTYLE ));
        if ( m_request == null || !absolute )
        {
            // FIXME: is rather slow
            return m_engine.getURL( context,
                                    page,
                                    params,
                                    absolute );
        }
        else 
        {
            String url = HttpUtil.makeBaseURLNoContext( m_request )
                   + m_engine.getURL( context, page, params, false );
            return url;
        }
    }

    /**
     *  Returns a shallow clone of the WikiContext.
     *
     *  @since 2.1.37.
     */
    public Object clone()
    {
        WikiContext copy = new WikiContext( m_engine, m_page );
        
        copy.m_requestContext = m_requestContext;
        copy.m_template       = m_template;
        copy.m_variableMap    = m_variableMap;
        copy.m_request        = m_request;
        copy.m_session        = m_session;
        return copy;
    }
    
    /**
     * Returns the WikiSession associated with the context.
     * This method is guaranteed to always return a valid WikiSession. 
     * If this context was constructed without an associated 
     * HttpServletRequest, it will return {@link WikiSession#guestSession()}.
     */  
    public WikiSession getWikiSession() 
    {
        return m_session;
    }
    
    /**
     *  This method can be used to find the WikiContext programmatically
     *  from a JSP PageContext.
     *  
     *  @since 2.4
     * @param pc
     * @return Current WikiContext, or null, of no context exists.
     */
    public static WikiContext findContext( PageContext pageContext )
    {
        return (WikiContext)pageContext.getAttribute( WikiTagBase.ATTR_CONTEXT, PageContext.REQUEST_SCOPE );
    }
    
    /**
     * Returns the permission required to successfully execute this context. 
     * For example, the a wiki context of VIEW for a certain page means that
     * the PagePermission "view" is required for the page. In some cases, no
     * particular permission is required, in which case a dummy permission will
     * be returned ({@link java.util.PropertyPermission}<code> "os.name",
     * "read"</code>). This method is guaranteed to always return a valid, 
     * non-null permission.
     * @return the permission
     * @since 2.4
     */
    public Permission requiredPermission()
    {
        return m_permission;
    }
    
    /**
     * Checks whether the current user has access to this wiki context,
     * by obtaining the required Permission ({@link #requiredPermission()}
     * and delegating to 
     * {@link com.ecyrd.jspwiki.AuthorizationManager#checkPermission(WikiSession, Permission)}.
     * If the user is allowed, this method will succeed silently. If
     * the context is not allowed, the user will be redirected to the
     * login page (if not logged in already) or to a standard 401/forbidden page
     * (if not).
     * @param response the HTTP response object for the users's current request
     * @return TODO
     */
    public boolean hasAccess( HttpServletResponse response ) throws Exception
    {
        //TODO: this class is, at best, a temporary placeholder for this method until we start using filters
        AuthorizationManager mgr = m_engine.getAuthorizationManager();
        Principal currentUser  = m_session.getUserPrincipal();
        
        // Log the access attempt
        NDC.push( m_engine.getApplicationName()+":"+ m_page.getName() );
        if ( log.isDebugEnabled() )
        {
            log.debug("Request for page '"+ m_page.getName() +"' from "
                      + m_request.getRemoteAddr()
                      + " by "+( getCurrentUser() != null ? getCurrentUser().getName() : "unknown user") );
        }
        
        if( !mgr.checkPermission( m_session, requiredPermission() ) )
        {
            if( m_session.isAuthenticated() )
            {
                log.info("User "+currentUser.getName()+" has no access - displaying message.");
                NDC.pop();
                response.sendError( HttpServletResponse.SC_FORBIDDEN, "You don't have enough privileges to do that." );
                return false;
            }
            else
            {
                log.info("User "+currentUser.getName()+" has no access - redirecting to login page.");
                String pageurl = m_engine.encodeName( m_page.getName() );
                NDC.pop();
                m_session.addMessage("You don't have access to '" + pageurl + "'. Log in first.");
                response.sendRedirect( m_engine.getBaseURL()+"Login.jsp?page="+pageurl );
                return false;
            }
        }
        NDC.pop();
        
        return true;
    }

    
    /**
     * Private method that updates the value returned by the {@link #requiredPermission()}
     * method. Will always be called when the page name, request context, or variable
     * changes.
     * @since 2.4
     */
    protected void updatePermission() 
    {
        // Dummy permission that should always be true; default if nothing else works
        m_permission = DUMMY_PERMISSION;
        
        // PageInfo.jsp
        if ( ATTACH.equals( m_requestContext ) )
        {
            m_permission = new PagePermission( m_page, "upload" );
        }
        else if ( COMMENT.equals( m_requestContext ) )
        {
            m_permission = new PagePermission( m_page, "comment" );
        }
        else if ( CONFLICT.equals( m_requestContext ) || DIFF.equals( m_requestContext )
                || INFO.equals( m_requestContext )    || PREVIEW.equals( m_requestContext )
                || RSS.equals( m_requestContext )     || VIEW.equals( m_requestContext ) )
        {
            m_permission = new PagePermission( m_page, "view" );
        }
        else if ( CREATE_GROUP.equals( m_requestContext ) )
        {
            m_permission = new WikiPermission( m_page.getWiki(), "createGroups" );
        }
        else if ( DELETE.equals( m_requestContext ) )
        {
            m_permission = new PagePermission( m_page, "delete" );
        }
        else if ( EDIT.equals( m_requestContext ) )
        {
            //
            //  Figure out which is the proper permission for this
            //  particular editing action.
            //
            boolean exists = false;
            try 
            {
                exists = m_engine.pageExists( m_page );
            }
            catch ( ProviderException e )
            {
            }
            if( exists )
            {
                m_permission = new PagePermission( m_page, "edit" );
            }
            else
            {
                if ( m_page.getName().startsWith( DefaultGroupManager.GROUP_PREFIX ) )
                    {
                    m_permission = new WikiPermission( m_page.getWiki(), "createGroups" );
                }
                else
                {
                    m_permission = new WikiPermission( m_page.getWiki(), "createPages" );
                }
            }
        }
        else if ( LOGIN.equals( m_requestContext ) )
        {
            m_permission = new WikiPermission( m_page.getWiki(), "login" );
        }
        else if ( PREFS.equals( m_requestContext ) )
        {
            m_permission = new WikiPermission( m_page.getWiki(), "editPreferences" );
        }
        else if ( REGISTER.equals( m_requestContext ) )
        {
            m_permission = new WikiPermission( m_page.getWiki(), "registerUser" );
        }
        else if ( RENAME.equals( m_requestContext ) )
        {
            m_permission = new PagePermission( m_page, "rename" );
        }
        else if ( UPLOAD.equals( m_requestContext ) )
        {
            m_permission = new PagePermission( m_page, "upload" );
        }
    }
}
