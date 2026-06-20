package org.example.backend_springboot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.backend_springboot.dto.vo.CategoryVO;
import org.example.backend_springboot.entity.Category;
import org.example.backend_springboot.mapper.CategoryMapper;
import org.example.backend_springboot.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<CategoryVO> listCategories(String type, Long parentId) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getStatus, 1);
        if (type != null && !type.isBlank()) {
            wrapper.eq(Category::getType, type);
        }
        if (parentId != null) {
            wrapper.eq(Category::getParentId, parentId);
        }
        wrapper.orderByAsc(Category::getSortOrder);
        return categoryMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setType(category.getType());
        vo.setParentId(category.getParentId());
        vo.setSortOrder(category.getSortOrder());
        return vo;
    }
}
