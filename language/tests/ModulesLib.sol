// A declaration-only named module included by ModulesMain.sol. Its function is reached through the
// module name with the `::` namespace separator (docs/LANGUAGE_SPEC.md section 20).

module math_utils {
    func double(value: Integer): Integer {
        return value * 2
    }
}
