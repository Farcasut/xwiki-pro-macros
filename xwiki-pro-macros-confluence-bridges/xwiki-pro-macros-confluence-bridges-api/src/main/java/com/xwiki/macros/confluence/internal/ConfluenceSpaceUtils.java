/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.macros.confluence.internal;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.confluence.resolvers.ConfluenceResolverException;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceKeyResolver;
import org.xwiki.contrib.confluence.resolvers.ConfluenceSpaceResolver;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Tools to manipulate migrated Confluence spaces.
 *
 * @version $Id$
 * @since 1.19.0
 */
@Component(roles = ConfluenceSpaceUtils.class)
@Singleton
@Unstable
public class ConfluenceSpaceUtils
{
    @Inject
    private Logger logger;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private ConfluenceSpaceKeyResolver confluenceSpaceKeyResolver;

    @Inject
    private ConfluenceSpaceResolver confluenceSpaceResolver;

    @Inject
    private EntityReferenceResolver<String> resolver;

    /**
     * This method is meant to handle macro parameters coming from Confluence that could contain both XWiki spaces
     * or Confluence (pseudo) space keys.
     *
     * @param spaceKeyOrRef the space key, or "@self", or a XWiki reference to the space.
     * @return the root of the Confluence space described by the parameter, or null if not found.
     */
    public EntityReference getSloppySpace(String spaceKeyOrRef)
    {
        try {
            if (spaceKeyOrRef.contains("@self")) {
                return confluenceSpaceResolver.getSpace(contextProvider.get().getDoc().getDocumentReference());
            }

            if (spaceKeyOrRef.indexOf(':') != -1 || spaceKeyOrRef.indexOf('.') != -1) {
                // This is a XWiki reference
                EntityReference spaceRef = resolver.resolve(spaceKeyOrRef, EntityType.SPACE);
                EntityReference webHome = new EntityReference("WebHome", EntityType.DOCUMENT, spaceRef);
                if (contextProvider.get().getWiki().exists(new DocumentReference(webHome), contextProvider.get())) {
                    // the home page of this space exists
                    return spaceRef;
                }
            }

            return confluenceSpaceKeyResolver.getSpaceByKey(spaceKeyOrRef);
        } catch (ConfluenceResolverException e) {
            logger.warn("Could not convert space [{}] to an entity reference", spaceKeyOrRef, e);
        } catch (XWikiException e) {
            logger.warn("Could not check document [{}] existence", spaceKeyOrRef, e);
        }

        return null;
    }
}


