package com.op1ed.appointmentmanager.shared.persistence;

import java.util.List;
import java.util.function.Function;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 数据访问层的分页结果。页号对业务保持从 0 开始，Mapper 中的 MyBatis-Plus 页号从 1 开始。 */
public record StoragePage<T>(List<T> content, int number, int size, long totalElements) {

    public StoragePage {
        content = List.copyOf(content);
    }

    public static <T> Page<T> request(int number, int size) {
        // 使用完整子查询统计，保留过滤条件；页号相加先转 long，避免整数溢出。
        return new Page<T>((long) number + 1, size).setOptimizeCountSql(false);
    }

    public static <T> StoragePage<T> from(IPage<T> page) {
        return new StoragePage<>(page.getRecords(), Math.toIntExact(page.getCurrent() - 1),
                Math.toIntExact(page.getSize()), page.getTotal());
    }

    public <R> StoragePage<R> map(Function<T, R> mapper) {
        return new StoragePage<>(content.stream().map(mapper).toList(), number, size, totalElements);
    }

    public List<T> getContent() { return content; }
    public int getNumber() { return number; }
    public int getSize() { return size; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() {
        return totalElements == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, 1 + (totalElements - 1) / size);
    }
}
