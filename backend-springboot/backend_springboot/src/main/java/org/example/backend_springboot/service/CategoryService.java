package org.example.backend_springboot.service;

import org.example.backend_springboot.dto.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    List<CategoryVO> listCategories(String type, Long parentId);
}
