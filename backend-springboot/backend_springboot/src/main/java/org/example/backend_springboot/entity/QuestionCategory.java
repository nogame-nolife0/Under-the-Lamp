package org.example.backend_springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_category")
public class QuestionCategory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Long categoryId;
    private LocalDateTime createdAt;
}
