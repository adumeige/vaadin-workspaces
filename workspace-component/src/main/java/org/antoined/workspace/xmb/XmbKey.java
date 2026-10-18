package org.antoined.workspace.xmb;

/** Keys on the XMB surface: a category header, or an item inside a category. */
public sealed interface XmbKey permits XmbKey.Category, XmbKey.Item {

    record Category<C>(C id) implements XmbKey {}

    record Item<C, I>(C category, I id) implements XmbKey {}
}
