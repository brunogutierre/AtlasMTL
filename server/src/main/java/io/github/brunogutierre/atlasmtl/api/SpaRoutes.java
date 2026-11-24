package io.github.brunogutierre.atlasmtl.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the Angular app for its own routes, so a link to /stats opens the stats page instead of a
 * 404. Listed one by one: a catch-all would also swallow mistyped API paths.
 */
@Configuration(proxyBeanMethods = false)
class SpaRoutes implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/stats").setViewName("forward:/index.html");
  }
}
