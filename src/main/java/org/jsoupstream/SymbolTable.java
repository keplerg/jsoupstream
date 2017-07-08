package org.jsoupstream;

import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
   Used to look up elements and return their behavior
 */
public class SymbolTable
{
    // The Element type
    public static enum Type
    {
        VOID_ELEMENT,
        RAW_TEXT_ELEMENT,
        ESCAPABLE_RAW_TEXT_ELEMENT,
        FOREIGN_ELEMENT,
        NORMAL_ELEMENT,
        UNKNOWN_ELEMENT
    }

    public static class Symbol
    {
        public String name;
        public SymbolTable.Type type;
        public List<String> implied; // these tags will close current

        public Symbol(String name, SymbolTable.Type type, List<String> implied)
        {
            this.name = name;
            this.type = type;
            this.implied = implied;
        }
    }

    public static final HashMap<String, Symbol> symbol_table = new HashMap<String, Symbol>();
    static {
        ArrayList<String> liImplied = new ArrayList<String>();
            liImplied.add("li");

        ArrayList<String> dtImplied = new ArrayList<String>();
            dtImplied.add("dt");
            dtImplied.add("dd");

        ArrayList<String> ddImplied = new ArrayList<String>();
            ddImplied.add("dd");
            ddImplied.add("dt");

        ArrayList<String> pImplied = new ArrayList<String>();
            pImplied.add("address");
            pImplied.add("article");
            pImplied.add("aside");
            pImplied.add("blockquote");
            pImplied.add("div");
            pImplied.add("dl");
            pImplied.add("fieldset");
            pImplied.add("footer");
            pImplied.add("form");
            pImplied.add("h1");
            pImplied.add("h2");
            pImplied.add("h3");
            pImplied.add("h4");
            pImplied.add("h5");
            pImplied.add("h6");
            pImplied.add("header");
            pImplied.add("hgroup");
            pImplied.add("hr");
            pImplied.add("main");
            pImplied.add("nav");
            pImplied.add("ol");
            pImplied.add("p");
            pImplied.add("pre");
            pImplied.add("section");
            pImplied.add("table");
            pImplied.add("ul");

        ArrayList<String> rbImplied = new ArrayList<String>();
            rbImplied.add("rb");
            rbImplied.add("rb");
            rbImplied.add("rt");
            rbImplied.add("rtc");
            rbImplied.add("rp");

        ArrayList<String> rtImplied = new ArrayList<String>();
            rtImplied.add("rb");
            rtImplied.add("rt");
            rtImplied.add("rtc");
            rtImplied.add("rp");

        ArrayList<String> rtcImplied = new ArrayList<String>();
            rtcImplied.add("rb");
            rtcImplied.add("rtc");
            rtcImplied.add("rp");

        ArrayList<String> rpImplied = new ArrayList<String>();
            rpImplied.add("rb");
            rpImplied.add("rt");
            rpImplied.add("rtc");
            rpImplied.add("rp");

        ArrayList<String> optgroupImplied = new ArrayList<String>();
            optgroupImplied.add("optgroup");

        ArrayList<String> optionImplied = new ArrayList<String>();
            optionImplied.add("option");
            optionImplied.add("optgroup");

        ArrayList<String> colgroupImplied = new ArrayList<String>();
            colgroupImplied.add("colgroup");

        ArrayList<String> theadImplied = new ArrayList<String>();
            theadImplied.add("tbody");
            theadImplied.add("tfoot");

        ArrayList<String> tbodyImplied = new ArrayList<String>();
            tbodyImplied.add("tbody");
            tbodyImplied.add("tfoot");

        ArrayList<String> tfootImplied = new ArrayList<String>();
            tfootImplied.add("tbody");

        ArrayList<String> trImplied = new ArrayList<String>();
            trImplied.add("tr");

        ArrayList<String> tdImplied = new ArrayList<String>();
            tdImplied.add("td");
            tdImplied.add("th");

        ArrayList<String> thImplied = new ArrayList<String>();
            thImplied.add("td");
            thImplied.add("th");


        symbol_table.put("area", new Symbol("area", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("base", new Symbol("base", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("br", new Symbol("br", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("col", new Symbol("col", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("embed", new Symbol("embed", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("hr", new Symbol("hr", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("img", new Symbol("img", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("input", new Symbol("input", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("keygen", new Symbol("keygen", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("link", new Symbol("link", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("meta", new Symbol("meta", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("param", new Symbol("param", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("source", new Symbol("source", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("track", new Symbol("track", SymbolTable.Type.VOID_ELEMENT, null));
        symbol_table.put("wbr", new Symbol("wbr", SymbolTable.Type.VOID_ELEMENT, null));

        symbol_table.put("script", new Symbol("script", SymbolTable.Type.RAW_TEXT_ELEMENT, null));
        symbol_table.put("style", new Symbol("style", SymbolTable.Type.RAW_TEXT_ELEMENT, null));

        symbol_table.put("textarea", new Symbol("textarea", SymbolTable.Type.ESCAPABLE_RAW_TEXT_ELEMENT, null));
        symbol_table.put("title", new Symbol("title", SymbolTable.Type.ESCAPABLE_RAW_TEXT_ELEMENT, null));

        symbol_table.put("math", new Symbol("math", SymbolTable.Type.FOREIGN_ELEMENT, null));
        symbol_table.put("svg", new Symbol("svg", SymbolTable.Type.FOREIGN_ELEMENT, null));

        symbol_table.put("a", new Symbol("a", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("abbr", new Symbol("abbr", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("acronym", new Symbol("acronym", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("address", new Symbol("address", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("applet", new Symbol("applet", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("article", new Symbol("article", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("aside", new Symbol("aside", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("audio", new Symbol("audio", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("b", new Symbol("b", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("basefont", new Symbol("basefont", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("bdi", new Symbol("bdi", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("bdo", new Symbol("bdo", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("big", new Symbol("big", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("blockquote", new Symbol("blockquote", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("body", new Symbol("body", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("button", new Symbol("button", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("canvas", new Symbol("canvas", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("caption", new Symbol("caption", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("center", new Symbol("center", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("cite", new Symbol("cite", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("code", new Symbol("code", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("colgroup", new Symbol("colgroup", SymbolTable.Type.NORMAL_ELEMENT, colgroupImplied));
        symbol_table.put("command", new Symbol("command", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("datalist", new Symbol("datalist", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("dd", new Symbol("dd", SymbolTable.Type.NORMAL_ELEMENT, ddImplied));
        symbol_table.put("del", new Symbol("del", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("details", new Symbol("details", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("dfn", new Symbol("dfn", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("dir", new Symbol("dir", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("div", new Symbol("div", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("div", new Symbol("div", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("dl", new Symbol("dl", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("dt", new Symbol("dt", SymbolTable.Type.NORMAL_ELEMENT, dtImplied));
        symbol_table.put("em", new Symbol("em", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("fieldset", new Symbol("fieldset", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("figcaption", new Symbol("figcaption", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("figure", new Symbol("figure", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("font", new Symbol("font", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("footer", new Symbol("footer", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("form", new Symbol("form", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("frame", new Symbol("frame", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("frameset", new Symbol("frameset", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("h1", new Symbol("h1", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("h2", new Symbol("h2", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("h3", new Symbol("h3", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("h4", new Symbol("h4", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("h5", new Symbol("h5", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("h6", new Symbol("h6", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("head", new Symbol("head", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("header", new Symbol("header", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("hgroup", new Symbol("hgroup", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("html", new Symbol("html", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("i", new Symbol("i", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("iframe", new Symbol("iframe", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("ins", new Symbol("ins", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("kbd", new Symbol("kbd", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("label", new Symbol("label", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("legend", new Symbol("legend", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("li", new Symbol("li", SymbolTable.Type.NORMAL_ELEMENT, liImplied));
        symbol_table.put("map", new Symbol("map", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("mark", new Symbol("mark", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("menu", new Symbol("menu", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("meter", new Symbol("meter", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("nav", new Symbol("nav", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("noframes", new Symbol("noframes", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("noscript", new Symbol("noscript", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("object", new Symbol("object", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("ol", new Symbol("ol", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("optgroup", new Symbol("optgroup", SymbolTable.Type.NORMAL_ELEMENT, optgroupImplied));
        symbol_table.put("option", new Symbol("option", SymbolTable.Type.NORMAL_ELEMENT, optionImplied));
        symbol_table.put("output", new Symbol("output", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("p", new Symbol("p", SymbolTable.Type.NORMAL_ELEMENT, pImplied));
        symbol_table.put("pre", new Symbol("pre", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("progress", new Symbol("progress", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("q", new Symbol("q", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("rb", new Symbol("rb", SymbolTable.Type.NORMAL_ELEMENT, rbImplied));
        symbol_table.put("rp", new Symbol("rp", SymbolTable.Type.NORMAL_ELEMENT, rpImplied));
        symbol_table.put("rt", new Symbol("rt", SymbolTable.Type.NORMAL_ELEMENT, rtImplied));
        symbol_table.put("rtc", new Symbol("rtc", SymbolTable.Type.NORMAL_ELEMENT, rtcImplied));
        symbol_table.put("ruby", new Symbol("ruby", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("s", new Symbol("s", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("samp", new Symbol("samp", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("section", new Symbol("section", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("select", new Symbol("select", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("small", new Symbol("small", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("span", new Symbol("span", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("strike", new Symbol("strike", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("strong", new Symbol("strong", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("sub", new Symbol("sub", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("summary", new Symbol("summary", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("sup", new Symbol("sup", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("table", new Symbol("table", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("tbody", new Symbol("tbody", SymbolTable.Type.NORMAL_ELEMENT, tbodyImplied));
        symbol_table.put("td", new Symbol("td", SymbolTable.Type.NORMAL_ELEMENT, tdImplied));
        symbol_table.put("tfoot", new Symbol("tfoot", SymbolTable.Type.NORMAL_ELEMENT, tfootImplied));
        symbol_table.put("th", new Symbol("th", SymbolTable.Type.NORMAL_ELEMENT, thImplied));
        symbol_table.put("thead", new Symbol("thead", SymbolTable.Type.NORMAL_ELEMENT, theadImplied));
        symbol_table.put("time", new Symbol("time", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("tr", new Symbol("tr", SymbolTable.Type.NORMAL_ELEMENT, trImplied));
        symbol_table.put("tt", new Symbol("tt", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("u", new Symbol("u", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("ul", new Symbol("ul", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("var", new Symbol("var", SymbolTable.Type.NORMAL_ELEMENT, null));
        symbol_table.put("video", new Symbol("video", SymbolTable.Type.NORMAL_ELEMENT, null));
    }

    public static Symbol lookup(String s)
    {
        Symbol sym = symbol_table.get( s.toLowerCase() );
        if ( sym != null )
        {
            return sym;
        }

        return new Symbol(s, SymbolTable.Type.UNKNOWN_ELEMENT, null);
    }
}
