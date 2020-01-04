/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

package org.apache.wiki.pages;

import net.sf.ehcache.CacheManager;
import org.apache.wiki.TestEngine;
import org.apache.wiki.WikiEngine;
import org.apache.wiki.WikiPage;
import org.apache.wiki.WikiProvider;
import org.apache.wiki.attachment.Attachment;
import org.apache.wiki.providers.BasicAttachmentProvider;
import org.apache.wiki.providers.CachingProvider;
import org.apache.wiki.providers.FileSystemProvider;
import org.apache.wiki.providers.VerySimpleProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collection;
import java.util.Properties;

public class DefaultPageManagerTest {

    static final String NAME1 = "Test1";

    TestEngine engine = TestEngine.build();

    @AfterEach
    public void tearDown() {
        final String files = engine.getWikiProperties().getProperty( FileSystemProvider.PROP_PAGEDIR );

        if( files != null ) {
            final File f = new File( files );
            TestEngine.deleteAll( f );
        }

        TestEngine.emptyWorkDir();
        CacheManager.getInstance().removeAllCaches();
    }

    @Test
    public void testPageCacheExists() throws Exception {
        engine.getWikiProperties().setProperty( "jspwiki.usePageCache", "true" );
        final PageManager m = new DefaultPageManager( engine, engine.getWikiProperties() );

        Assertions.assertTrue( m.getProvider() instanceof CachingProvider );
    }

    @Test
    public void testPageCacheNotInUse() throws Exception {
        engine.getWikiProperties().setProperty( "jspwiki.usePageCache", "false" );
        final PageManager m = new DefaultPageManager( engine, engine.getWikiProperties() );

        Assertions.assertFalse( m.getProvider() instanceof CachingProvider );
    }

    @Test
    public void testDeletePage() throws Exception {
        engine.saveText( NAME1, "Test" );
        final String files = engine.getWikiProperties().getProperty( FileSystemProvider.PROP_PAGEDIR );
        final File saved = new File( files, NAME1+FileSystemProvider.FILE_EXT );
        Assertions.assertTrue( saved.exists(), "Didn't create it!" );

        final WikiPage page = engine.getPageManager().getPage( NAME1, WikiProvider.LATEST_VERSION );
        engine.getPageManager().deletePage( page.getName() );
        Assertions.assertFalse( saved.exists(), "Page has not been removed!" );
    }

    @Test
    public void testDeletePageAndAttachments() throws Exception {
        engine.saveText( NAME1, "Test" );
        final Attachment att = new Attachment( engine, NAME1, "TestAtt.txt" );
        att.setAuthor( "FirstPost" );
        engine.getAttachmentManager().storeAttachment( att, engine.makeAttachmentFile() );

        final String files = engine.getWikiProperties().getProperty( FileSystemProvider.PROP_PAGEDIR );
        final File saved = new File( files, NAME1+FileSystemProvider.FILE_EXT );

        final String atts = engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR );
        final File attfile = new File( atts, NAME1+"-att/TestAtt.txt-dir" );

        Assertions.assertTrue( saved.exists(), "Didn't create it!" );
        Assertions.assertTrue( attfile.exists(), "Attachment dir does not exist" );

        final WikiPage page = engine.getPageManager().getPage( NAME1, WikiProvider.LATEST_VERSION );

        engine.getPageManager().deletePage( page.getName() );

        Assertions.assertFalse( saved.exists(), "Page has not been removed!" );
        Assertions.assertFalse( attfile.exists(), "Attachment has not been removed" );
    }

    @Test
    public void testDeletePageAndAttachments2() throws Exception {
        engine.saveText( NAME1, "Test" );
        Attachment att = new Attachment( engine, NAME1, "TestAtt.txt" );
        att.setAuthor( "FirstPost" );
        engine.getAttachmentManager().storeAttachment( att, engine.makeAttachmentFile() );

        final String files = engine.getWikiProperties().getProperty( FileSystemProvider.PROP_PAGEDIR );
        final File saved = new File( files, NAME1+FileSystemProvider.FILE_EXT );

        final String atts = engine.getWikiProperties().getProperty( BasicAttachmentProvider.PROP_STORAGEDIR );
        final File attfile = new File( atts, NAME1+"-att/TestAtt.txt-dir" );

        Assertions.assertTrue( saved.exists(), "Didn't create it!" );
        Assertions.assertTrue( attfile.exists(), "Attachment dir does not exist" );

        final WikiPage page = engine.getPageManager().getPage( NAME1, WikiProvider.LATEST_VERSION );
        Assertions.assertNotNull( page, "page" );

        att = engine.getAttachmentManager().getAttachmentInfo(NAME1+"/TestAtt.txt");
        engine.getPageManager().deletePage(att.getName());
        engine.getPageManager().deletePage( NAME1 );
        Assertions.assertNull( engine.getPageManager().getPage(NAME1), "Page not removed" );
        Assertions.assertNull( engine.getPageManager().getPage(NAME1+"/TestAtt.txt"), "Att not removed" );

        final Collection< String > refs = engine.getReferenceManager().findReferrers(NAME1);
        Assertions.assertNull( refs, "referrers" );
    }

    @Test
    public void testDeleteVersion() throws Exception {
        final Properties props = engine.getWikiProperties();
        props.setProperty( "jspwiki.pageProvider", "VersioningFileProvider" );
        final TestEngine engine = new TestEngine( props );
        engine.saveText( NAME1, "Test1" );
        engine.saveText( NAME1, "Test2" );
        engine.saveText( NAME1, "Test3" );

        final WikiPage page = engine.getPageManager().getPage( NAME1, 3 );
        engine.getPageManager().deleteVersion( page );
        Assertions.assertNull( engine.getPageManager().getPage( NAME1, 3 ), "got page" );

        final String content = engine.getText( NAME1, WikiProvider.LATEST_VERSION );
        Assertions.assertEquals( "Test2", content.trim(), "content" );
    }

    @Test
    public void testDeleteVersion2() throws Exception {
        final Properties props = engine.getWikiProperties();
        props.setProperty( "jspwiki.pageProvider", "VersioningFileProvider" );
        final TestEngine engine = new TestEngine( props );
        engine.saveText( NAME1, "Test1" );
        engine.saveText( NAME1, "Test2" );
        engine.saveText( NAME1, "Test3" );

        final WikiPage page = engine.getPageManager().getPage( NAME1, 1 );
        engine.getPageManager().deleteVersion( page );
        Assertions.assertNull( engine.getPageManager().getPage( NAME1, 1 ), "got page" );

        final String content = engine.getText( NAME1, WikiProvider.LATEST_VERSION );
        Assertions.assertEquals( "Test3", content.trim(), "content" );
        Assertions.assertEquals( "", engine.getText(NAME1, 1).trim(), "content1" );
    }

    @Test
    public void testLatestGet() throws Exception {
        final Properties props = engine.getWikiProperties();
        props.setProperty( "jspwiki.pageProvider", "org.apache.wiki.providers.VerySimpleProvider" );
        props.setProperty( "jspwiki.usePageCache", "false" );
        final WikiEngine engine = new TestEngine( props );
        final WikiPage p = engine.getPageManager().getPage( "test", -1 );
        final VerySimpleProvider vsp = (VerySimpleProvider) engine.getPageManager().getProvider();

        Assertions.assertEquals( "test", vsp.m_latestReq, "wrong page" );
        Assertions.assertEquals( -1, vsp.m_latestVers, "wrong version" );
        Assertions.assertNotNull( p, "null" );
    }

    @Test
    public void testLatestGet2() throws Exception {
        final Properties props = engine.getWikiProperties();
        props.setProperty( "jspwiki.pageProvider", "org.apache.wiki.providers.VerySimpleProvider" );
        props.setProperty( "jspwiki.usePageCache", "false" );
        final WikiEngine engine = new TestEngine( props );
        final String p = engine.getText( "test", -1 );
        final VerySimpleProvider vsp = (VerySimpleProvider) engine.getPageManager().getProvider();

        Assertions.assertEquals( "test", vsp.m_latestReq, "wrong page" );
        Assertions.assertEquals( -1, vsp.m_latestVers, "wrong version" );
        Assertions.assertNotNull( p, "null" );
    }

    @Test
    public void testLatestGet3() throws Exception {
        final Properties props = engine.getWikiProperties();
        props.setProperty( "jspwiki.pageProvider", "org.apache.wiki.providers.VerySimpleProvider" );
        props.setProperty( "jspwiki.usePageCache", "false" );
        final WikiEngine engine = new TestEngine( props );
        final String p = engine.getHTML( "test", -1 );
        final VerySimpleProvider vsp = (VerySimpleProvider) engine.getPageManager().getProvider();

        Assertions.assertEquals( "test", vsp.m_latestReq, "wrong page" );
        Assertions.assertEquals( 5, vsp.m_latestVers, "wrong version" );
        Assertions.assertNotNull( p, "null" );
    }

    @Test
    public void testLatestGet4() throws Exception {
        final Properties props = engine.getWikiProperties();
        props.setProperty( "jspwiki.pageProvider", "org.apache.wiki.providers.VerySimpleProvider" );
        props.setProperty( "jspwiki.usePageCache", "true" );
        final WikiEngine engine = new TestEngine( props );
        final String p = engine.getHTML( VerySimpleProvider.PAGENAME, -1 );
        final CachingProvider cp = (CachingProvider)engine.getPageManager().getProvider();
        final VerySimpleProvider vsp = (VerySimpleProvider) cp.getRealProvider();

        Assertions.assertEquals( VerySimpleProvider.PAGENAME, vsp.m_latestReq, "wrong page" );
        Assertions.assertEquals( -1, vsp.m_latestVers,  "wrong version" );
        Assertions.assertNotNull( p, "null" );
    }

}
