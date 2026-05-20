package com.einvoice.core.codegen;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.domain.shared.LifecycleTransitions;
import com.einvoice.core.domain.shared.TransactionType;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates eta-states.ts from Java enum + lifecycle definitions.
 * Emits separate invoiceTransitions and receiptTransitions maps.
 */
public final class EtaStatesTsGenerator {

    private static final String HEADER = """
            // THIS FILE IS GENERATED -- DO NOT EDIT
            // Generated from DocumentState Java enum
            // and LifecycleTransitions transition matrices.
            // Re-run: mvn -pl platform-core process-classes
            """;

    private EtaStatesTsGenerator() {
    }

    /**
     * Entry point.
     *
     * @param args first argument is output path
     * @throws IOException if write fails
     */
    public static void main(String[] args) throws IOException {
        Path outputPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get(
                        "../frontend/src/app/invoices/shared/generated/eta-states.ts");

        Files.createDirectories(outputPath.getParent());

        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');

        appendEnumConst(sb, "DocumentState", DocumentState.values());
        appendEnumConst(sb, "LifecycleAction", LifecycleAction.values());

        appendTransitionMap(sb, "invoiceTransitions", DocumentState.values(),
                (state, action) -> LifecycleTransitions.allowed(
                        state, action, TransactionType.INVOICE),
                (state, action) -> LifecycleTransitions.next(
                        state, action, TransactionType.INVOICE));
        appendTransitionMap(sb, "receiptTransitions", DocumentState.values(),
                (state, action) -> LifecycleTransitions.allowed(
                        state, action, TransactionType.RECEIPT),
                (state, action) -> LifecycleTransitions.next(
                        state, action, TransactionType.RECEIPT));

        sb.append("""
                export function isAllowed(
                    state: string, action: string,
                    map?: Record<string, Record<string, string | null>>
                ): boolean {
                  const t = map ?? invoiceTransitions;
                  return t[state]?.[action] !== undefined;
                }

                export function nextState(
                    state: string, action: string,
                    map?: Record<string, Record<string, string | null>>
                ): string | null {
                  const t = map ?? invoiceTransitions;
                  const stateTransitions = t[state];
                  if (!stateTransitions || !(action in stateTransitions)) {
                    throw new Error(
                        `Invalid lifecycle transition: ${state} + ${action}`);
                  }
                  return stateTransitions[action];
                }
                """);

        try (Writer w = Files.newBufferedWriter(outputPath)) {
            w.write(sb.toString());
        }
    }

    private static void appendEnumConst(StringBuilder sb, String name,
            Object[] values) {
        sb.append("export const ").append(name).append(" = {\n");
        for (Object v : values) {
            sb.append("  ").append(v.toString()).append(": '")
                    .append(v.toString()).append("',\n");
        }
        sb.append("} as const;\n\n");
        final String typeName = name + "Type";
        sb.append("export type ").append(typeName)
                .append(" = typeof ").append(name)
                .append("[keyof typeof ").append(name).append("];\n\n");
    }

    private static <S extends Enum<S>> void appendTransitionMap(
            StringBuilder sb, String mapName, S[] states,
            java.util.function.BiFunction<S, LifecycleAction, Boolean> allowedFn,
            java.util.function.BiFunction<S, LifecycleAction, S> nextFn) {
        sb.append("export const ").append(mapName)
                .append(": Record<string, Record<string, string | null>>")
                .append(" = {\n");
        for (S state : states) {
            sb.append("  ").append(state.name()).append(": {\n");
            Map<String, String> sorted = new TreeMap<>();
            for (LifecycleAction action : LifecycleAction.values()) {
                if (allowedFn.apply(state, action)) {
                    S next = nextFn.apply(state, action);
                    sorted.put(action.name(),
                            next == null ? null : next.name());
                }
            }
            for (var entry : sorted.entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": ");
                if (entry.getValue() == null) {
                    sb.append("null");
                } else {
                    sb.append("'").append(entry.getValue()).append("'");
                }
                sb.append(",\n");
            }
            sb.append("  },\n");
        }
        sb.append("};\n\n");
    }
}
