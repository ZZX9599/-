package com.zzx.anno;

import java.lang.annotation.*;

/**
 * @author ZZX
 * @version 1.0.0
 * @date 2023:04:29 19:51:01
 */

@Target(value = ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Idempotence {
}
