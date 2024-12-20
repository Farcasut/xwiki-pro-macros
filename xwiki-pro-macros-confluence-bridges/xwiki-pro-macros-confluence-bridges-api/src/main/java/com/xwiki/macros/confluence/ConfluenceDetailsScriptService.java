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
package com.xwiki.macros.confluence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.TableCellBlock;
import org.xwiki.rendering.block.TableRowBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.BlockMatcher;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

import static com.xwiki.macros.confluence.internal.XDOMUtils.getMacroXDOM;
import static org.apache.commons.lang3.StringUtils.defaultString;

/**
 * Confluence details script services. Mostly to implement the detailssummary macro.
 * @since 1.19.0
 * @version $Id$
 */
@Component
@Singleton
@Named("confluence.details")
@Unstable
public class ConfluenceDetailsScriptService implements ScriptService
{
    private static final ClassBlockMatcher MACRO_MATCHER = new ClassBlockMatcher(MacroBlock.class);

    private static final String ID = "id";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceResolver<String> resolver;

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainTextRenderer;

    @Inject
    @Named("xwiki/2.1")
    private BlockRenderer xwikiSyntaxRenderer;

    @Inject
    private Provider<ComponentManager> componentManagerProvider;

    @Inject
    private Logger logger;


    private XDOM findDetailsMacro(XDOM xdom, String syntaxId, String id)
    {
        List<MacroBlock> macros = xdom.getBlocks(MACRO_MATCHER, Block.Axes.DESCENDANT_OR_SELF);
        for (MacroBlock macroBlock : macros) {
            try {
                if (StringUtils.equals("confluence_details", macroBlock.getId())) {
                    String dId = defaultString(id);
                    if (dId.isEmpty() || StringUtils.equals(dId, defaultString(macroBlock.getParameter(ID)))) {
                        return getMacroXDOM(componentManagerProvider.get(), macroBlock, syntaxId);
                    }
                } else {
                    XDOM macroXDOM = getMacroXDOM(componentManagerProvider.get(), macroBlock, syntaxId);
                    if (macroXDOM != null) {
                        return findDetailsMacro(macroXDOM, syntaxId, id);
                    }
                }
            } catch (ComponentLookupException e) {
                logger.error("Component lookup error trying to find the confluence_details macro", e);
            }
        }
        return null;
    }

    /**
     * @return the rows to display in the detailssummary macro given the provided details id, the headings parameter
     *          and the results
     * @param id the id of the details macros to consider
     * @param headingsParam the headings confluence parameter
     * @param results the results of the CQL query of the detailsummary macro
     */
    public List<List<String>> getDetails(String id, String headingsParam, List<Map<String, Object>> results)
    {
        List<String> headings = parseHeadings(headingsParam);
        List<String> columns = headings.isEmpty() ? new ArrayList<>() : headings;
        List<String> columnsLower = headings.isEmpty()
            ? new ArrayList<>()
            : headings.stream().map(String::toLowerCase).collect(Collectors.toList());

        List<List<String>> rows = new ArrayList<>(results.size());
        rows.add(columns);
        for (Map<String, Object> response : results) {
            String fullName = response.get("fullname").toString();
            EntityReference docRef = resolver.resolve(fullName, EntityType.DOCUMENT);
            XWikiContext context = contextProvider.get();
            XWikiDocument doc;
            try {
                doc = context.getWiki().getDocument(docRef, context);
            } catch (XWikiException e) {
                logger.error("Could not get the document", e);
                continue;
            }

            XDOM details = findDetailsMacro(doc.getXDOM(), doc.getSyntax().toIdString(), id);
            if (details != null) {
                List<String> row = getRow(details, headings, columns, columnsLower);
                row.add(0, fullName);
                rows.add(row);
            }
        }

        return rows;
    }

    private List<String> getRow(XDOM xdomDetails, List<String> headings, List<String> columns,
        List<String> columnsLower)
    {
        BlockMatcher rowMatcher = new ClassBlockMatcher(TableRowBlock.class);
        BlockMatcher cellMatcher = new ClassBlockMatcher(TableCellBlock.class);
        List<TableRowBlock> xdomRows = xdomDetails.getBlocks(rowMatcher, Block.Axes.DESCENDANT_OR_SELF);
        List<String> row = new ArrayList<>(
            1 + (headings.isEmpty()
                ? Math.max(columns.size(), xdomRows.size())
                : headings.size())
        );
        for (TableRowBlock xdomRow : xdomRows) {
            List<TableCellBlock> cells = xdomRow.getBlocks(cellMatcher, Block.Axes.DESCENDANT_OR_SELF);
            if (cells.size() < 2) {
                continue;
            }
            DefaultWikiPrinter printer = new DefaultWikiPrinter();
            plainTextRenderer.render(cells.get(0), printer);
            String key = printer.toString().trim();

            printer.clear();
            xwikiSyntaxRenderer.render(cells.get(1).getChildren(), printer);

            String value = printer.toString().trim();

            String keyLower = key.toLowerCase();
            int index = columnsLower.indexOf(keyLower);
            if (index == -1) {
                if (!headings.isEmpty()) {
                    // if the headings was specified by the user, don't add columns
                    continue;
                }
                index = columns.size();
                columns.add(key);
                columnsLower.add(keyLower);
            }
            while (index >= row.size()) {
                row.add("");
            }
            row.set(index, value);
        }
        return row;
    }

    private List<String> parseHeadings(String headingsParam)
    {
        if (StringUtils.isEmpty(headingsParam)) {
            return Collections.emptyList();
        }

        List<String> headings = new ArrayList<>(StringUtils.countMatches(headingsParam, ','));
        int i = 0;
        int len = headingsParam.length();
        StringBuilder heading = new StringBuilder();
        while (i < len) {
            char c = headingsParam.charAt(i);
            if (c == '"') {
                i++;
                while (i < len) {
                    c = headingsParam.charAt(i);
                    if (c == '"') {
                        break;
                    }

                    if (c == '\\') {
                        i++;
                        c = headingsParam.charAt(i);
                    }
                    heading.append(c);
                    i++;
                }
            } else if (c == ',') {
                headings.add(heading.toString().trim());
                heading.setLength(0);
            } else {
                heading.append(c);
            }
            i++;
        }

        if (heading.length() > 0) {
            headings.add(heading.toString().trim());
        }

        return headings;
    }
}
