package com.eventrush.api;

import com.eventrush.domain.EventCategory;
import com.eventrush.service.EventCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EventCategoryController {

    private final EventCategoryService service;

    EventCategoryController(EventCategoryService service) {
        this.service = service;
    }

    @GetMapping("/api/catalog/categories")
    List<EventCategory> listPublic() {
        return service.listPublic();
    }

    @GetMapping("/api/organizer/catalog/categories")
    List<EventCategory> listAll() {
        return service.listAll();
    }

    @PostMapping("/api/organizer/catalog/categories")
    EventCategory create(@Valid @RequestBody CategoryRequest request) {
        return service.create(request.name(), request.iconKey(), request.displayOrder(), request.enabled());
    }

    @PutMapping("/api/organizer/catalog/categories/{categoryId}")
    EventCategory update(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request
    ) {
        return service.update(categoryId, request.name(), request.iconKey(),
                request.displayOrder(), request.enabled());
    }

    record CategoryRequest(
            @NotBlank(message = "类目名称不能为空")
            @Size(max = 40, message = "类目名称不能超过 40 个字") String name,
            @Size(max = 40, message = "图标标识不能超过 40 个字") String iconKey,
            @Min(value = 0, message = "展示顺序不能小于 0")
            @Max(value = 999, message = "展示顺序不能超过 999") int displayOrder,
            boolean enabled
    ) {
    }
}
