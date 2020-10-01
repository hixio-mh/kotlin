// FIR_IDENTICAL
fun foo(numbers: Collection<Int>) {
    for (i in numbers) {
        val b: Boolean
        if (1 < 2) {
            <!VAL_REASSIGNMENT!>b<!> = false
        }
        else {
            <!VAL_REASSIGNMENT!>b<!> = true
        }
        use(b)
        continue
    }
}

fun use(vararg a: Any?) = a
