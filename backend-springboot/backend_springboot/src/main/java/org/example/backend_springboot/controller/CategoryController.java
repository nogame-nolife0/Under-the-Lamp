package org.example.backend_springboot.controller;

import org.example.backend_springboot.common.Result;
import org.example.backend_springboot.dto.vo.CategoryVO;
import org.example.backend_springboot.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result<List<CategoryVO>> list(@RequestParam(required = false) String type,
                                         @RequestParam(required = false) Long parentId) {
        return Result.success(categoryService.listCategories(type, parentId));
    }
}
