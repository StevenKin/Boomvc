package me.stevenkin.boomvc.mvc.filter.imp;

import me.stevenkin.boomvc.common.dispatcher.MvcDispatcher;
import me.stevenkin.boomvc.common.filter.Filter;
import me.stevenkin.boomvc.common.filter.FilterChain;
import me.stevenkin.boomvc.http.HttpRequest;
import me.stevenkin.boomvc.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DefaultFilterChain implements FilterChain {
    private static final Logger logger = LoggerFactory.getLogger(DefaultFilterChain.class);

    private List<Filter> filters = new ArrayList<>();

    private MvcDispatcher dispatcher;

    private int currentIndex = 0;

    @Override
    public FilterChain addFilter(Filter f) {
        this.filters.add(f);
        return this;
    }

    @Override
    public void doFilter(HttpRequest request, HttpResponse response) throws Exception{
        if(this.currentIndex >= this.filters.size()) {
            this.dispatcher.dispatcher(request, response);
            return;
        }
        try {
            this.filters.get(this.currentIndex++).doFilter(request, response, this);
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    @Override
    public FilterChain dispatcher(MvcDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        return this;
    }


    @Override
    public void destroy() {
        this.filters.forEach(f->f.destroy());
        this.dispatcher.destroy();
    }
}
