package org.jeecg.loader.repository;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.loader.repository.MyInMemoryRouteDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Dynamic route gateway service
 * 1) Implements Spring ApplicationEventPublisherAware for event publishing
 * 2) Provides add/update/delete route methods and publish capability via bean access.
 *
 * @author zyf
 */
@Slf4j
@Service
public class DynamicRouteService implements ApplicationEventPublisherAware {

    @Autowired
    private MyInMemoryRouteDefinitionRepository repository;

    /**
     * Publish event.
     */

    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    /**
     * Delete route.
     *
     * @param id
     * @return
     */
    public synchronized void delete(String id) {
        try {
            repository.delete(Mono.just(id)).subscribe();
            this.publisher.publishEvent(new RefreshRoutesEvent(this));
        }catch (Exception e){
            log.warn("operation failed", e);
        }
    }

    /**
     * Update route
     *
     * @param definition
     * @return
     */
    public synchronized String update(RouteDefinition definition) {
        try {
            log.info("gateway update route {}", definition);
        } catch (Exception e) {
            return "update fail,not find route  routeId: " + definition.getId();
        }
        try {
            repository.save(Mono.just(definition)).subscribe();
            this.publisher.publishEvent(new RefreshRoutesEvent(this));
            return "success";
        } catch (Exception e) {
            return "update route fail";
        }
    }

    /**
     * Add route
     *
     * @param definition
     * @return
     */
    public synchronized String add(RouteDefinition definition) {
        log.info("gateway add route {}", definition);
        try {
            repository.save(Mono.just(definition)).subscribe();
        } catch (Exception e) {
            log.warn("operation failed", e);
        }
        return "success";
    }
}