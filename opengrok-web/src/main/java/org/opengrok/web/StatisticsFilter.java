/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License (the "License").
 * You may not use this file except in compliance with the License.
 *
 * See LICENSE.txt included in this distribution for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at LICENSE.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 */
package org.opengrok.web;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import org.opengrok.indexer.Metrics;
import org.opengrok.indexer.web.PageConfig;
import org.opengrok.indexer.web.Prefix;
import org.opengrok.indexer.web.SearchHelper;

public class StatisticsFilter implements Filter {

    static final String REQUESTS_METRIC = "requests";

    private final DistributionSummary requests = Metrics.getRegistry().summary(REQUESTS_METRIC);

    private final Timer genericTimer = Metrics.getRegistry().timer("*");
    private final Timer emptySearch = Metrics.getRegistry().timer("empty_search");
    private final Timer successfulSearch = Metrics.getRegistry().timer("successful_search");

    @Override
    public void init(FilterConfig fc) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) sr;

        Instant start = Instant.now();

        PageConfig config = PageConfig.get(httpReq);

        fc.doFilter(sr, sr1);

        Duration duration = Duration.between(start, Instant.now());

        String category;
        if (isRoot(httpReq)) {
            category = "root";
        } else if (config.getPrefix() != Prefix.UNKNOWN) {
            category = config.getPrefix().toString().substring(1);
        } else {
            return;
        }

        /*
         * Add the request to the statistics. Be aware of the colliding call in
         * {@code AuthorizationFilter#doFilter}.
         */
        requests.record(1);
        genericTimer.record(duration);

        Metrics.getRegistry().timer(category).record(duration);

        /* supplementary categories */
        if (config.getProject() != null) {
            Metrics.getRegistry()
                    .timer("viewing_of_" + config.getProject().getName())
                    .record(duration);
        }

        SearchHelper helper = (SearchHelper) config.getRequestAttribute(SearchHelper.REQUEST_ATTR);
        if (helper != null) {
            if (helper.hits == null || helper.hits.length == 0) {
                // empty search
                emptySearch.record(duration);
            } else {
                // successful search
                successfulSearch.record(duration);
            }
        }
    }

    private boolean isRoot(final HttpServletRequest httpReq) {
        return httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("/")
                || httpReq.getRequestURI().replace(httpReq.getContextPath(), "").equals("");
    }

    @Override
    public void destroy() {
    }
}