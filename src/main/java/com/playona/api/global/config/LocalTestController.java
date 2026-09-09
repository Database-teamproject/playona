package com.playona.api.global.config;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
public class LocalTestController {
  @GetMapping(value = "/local", produces = MediaType.TEXT_HTML_VALUE)
  public Resource testPage() {
    return new ClassPathResource("local/index.html");
  }
}
