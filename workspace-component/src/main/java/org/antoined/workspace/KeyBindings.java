package org.antoined.workspace;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.dom.DomListenerRegistration;
import com.vaadin.flow.shared.Registration;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Server-side key bindings for leaves. The only client-side logic is a guard that ignores
 * keys typed into text inputs, so arrows never hijack a field inside an item.
 */
public final class KeyBindings {

    private KeyBindings() {}

    private static final String INTERACTIVE_GUARD =
            "(function(){let el=document.activeElement;"
            + "while(el&&el.shadowRoot&&el.shadowRoot.activeElement)el=el.shadowRoot.activeElement;"
            + "if(!el||el===document.body)return false;"
            + "const t=el.tagName.toLowerCase();"
            + "return t==='input'||t==='textarea'||t==='select'||el.isContentEditable;})()";

    /**
     * Listen for the given {@code event.key} values on the scope and call the handler with the key.
     * The default action is prevented for matching keys.
     */
    public static Registration bind(Component scope, Collection<String> keys, Consumer<String> handler) {
        String keyList = keys.stream().map(k -> "'" + k + "'").collect(Collectors.joining(","));
        DomListenerRegistration reg = scope.getElement()
                .addEventListener("keydown", e -> handler.accept(e.getEventData().getString("event.key")))
                .addEventData("event.key")
                .setFilter("[" + keyList + "].includes(event.key) && !" + INTERACTIVE_GUARD)
                .preventDefault()
                .stopPropagation();
        return reg;
    }
}
