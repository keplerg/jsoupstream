# jsoupstream
A streaming library to parse HTML and uses CSS Selectors with callback functions to make modifications. 

To build the library either use gradle:

```gradle
gradle clean build jar
```

or ant:

```ant
ant clean build jar
```

The library uses CSS selectors to identify elements to be operated on. The syntax of CSS Selectors is:
```css
  /*
  Some comment here
  */
  selector1 { action1; action2; ... }
  selector2 { actionn; actionn+1; ... }
```

There are predefined actions in the org.jsoupstream.Functions class. These actions do not need to be qualified with the package/class. If you write your own actions, you will need to specify the fully qualified package/class/method:
```css
  /* Example using custom action */
  h1 { com.example.MyFunctions.method('parameter') }
```

Here is an example to remove all scripts from a page:
```css
  script { delete }
```

Here is an example to replace the first H1 heading on a page:
```css
  body h1:count(1) { replaceText('My New Title') }
```

You can also chain actions together separated bu a semicolon:
```css
  img { addAttribute('data-src', 'example.com'); addAttribute('class', 'newClass') }
```


A selector is a chain of simple selectors, separated by combinators. Selectors are case insensitive (including against elements, attributes, and attribute values).

The universal selector (*) is implicit when no element selector is supplied (i.e. *.header and .header are equivalent).

JSoupStream was written as an alternative to JSoup. JSoup differs in that the entire HTML document is read in to memory as a Document tree (DOM). JSoupStream is a streaming parser and only holds the list of tokens that are required to either perform a Selector match or execute a set of actions on a matched element. Both libraries have their strengths and weaknesses. JSoup generally requires more memory but has more functionality and runs slightly faster when the garbage collector is not overtaxed. JSoupStream can run significantly faster than JSoup if all the selectors are satified either by reaching the :count() or by calling the done action or setDone in a custom action. This short circuits parsing and the rest of the document is written directly out. JSoupStream also preserves whitespace (formatting) while JSoup does not.


Below is a comparison table of the JSoup and JSoupStream supported Selector syntax:


CSS|Description|Example|Jsoup|JSoupStream
---|-----------|-------|-----|-----------
tag|elements with the given tag name|div|Y|Y
*\|E|elements of type E in any namespace ns|*\|name finds <fb:name> elements|Y|N
ns\|E|elements of type E in the namespace ns|fb\|name finds <fb:name> elements|Y|N
#id|elements with attribute ID of "id"|div#wrap, #logo|Y|Y
.class|elements with a class name of "class"|div.left, .result|Y|Y
[attr]|elements with an attribute named "attr" (with any value)|a[href], [title]|Y|Y
[^attrPrefix]|elements with an attribute name starting with "attrPrefix". Use to find elements with HTML5 datasets|[^data-], div[^data-]|Y|Y
[attr=val]|elements with an attribute named "attr", and value equal to "val"|img[width=500], a[rel=nofollow]|Y|Y
[attr="val"]|elements with an attribute named "attr", and value equal to "val"|span[hello="Cleveland"][goodbye="Columbus"], a[rel="nofollow"]|Y|Y
[attr^=valPrefix]|elements with an attribute named "attr", and value starting with "valPrefix"|a[href^=http:]|Y|Y
[attr$=valSuffix]|elements with an attribute named "attr", and value ending with "valSuffix"|img[src$=.png]|Y|Y
[attr*=valContaining]|elements with an attribute named "attr", and value containing "valContaining"|a[href*=/search/]|Y|Y
[attr~=regex]|elements with an attribute named "attr", and value matching the regular expression|img[src~=(?i)\\.(png\|jpe?g)]|Y|Y
E F|an F element descended from an E element|div a, .logo h1|Y|Y
E > F|an F direct child of E|ol > li|Y|Y
E + F|an F element immediately preceded by sibling E|li + li, div.head + div|Y|Y
E ~ F|an F element preceded by sibling E|h1 ~ p|Y|Y
E, F, G|all matching elements E, F, or G|a[href], div, h3|Y|N
:lt(n)|elements whose sibling index is less than n|td:lt(3) finds the first 3 cells of each row|Y|N
:gt(n)|elements whose sibling index is greater than n|td:gt(1) finds cells after skipping the first two|Y|N
:eq(n)|elements whose sibling index is equal to n|td:eq(0) finds the first cell of each row|Y|N
:has(selector)|elements that contains at least one element matching the selector|div:has(p) finds divs that contain p elements|Y|N
:not(selector)|elements that do not match the selector. See also Elements.not(String)|div:not(.logo) finds all divs that do not have the "logo" class.|Y|N
:contains(text)|elements that contains the specified text. The search is case insensitive. The text may appear in the found element, or any of its descendants.|p:contains(jsoup) finds p elements containing the text "jsoup".|Y|N
:matches(regex)|elements whose text matches the specified regular expression. The text may appear in the found element, or any of its descendants.|td:matches(\\d+) finds table cells containing digits. div:matches((?i)login) finds divs containing the text, case insensitively.|Y|N
:containsOwn(text)|elements that directly contain the specified text. The search is case insensitive. The text must appear in the found element, not any of its descendants.|p:containsOwn(jsoup) finds p elements with own text "jsoup".|Y|N
:matchesOwn(regex)|elements whose own text matches the specified regular expression. The text must appear in the found element, not any of its descendants.|td:matchesOwn(\\d+) finds table cells directly containing digits. div:matchesOwn((?i)login) finds divs containing the text, case insensitively.|Y|N
:containsData(data)|elements that contains the specified data. The contents of script and style elements, and comment nodes (etc) are considered data nodes, not text nodes. The search is case insensitive. The data may appear in the found element, or any of its descendants.|script:contains(jsoup) finds script elements containing the data "jsoup".|Y|N
:root|The element that is the root of the document. In HTML, this is the html element|:root|Y|N
:nth-child(an+b)|elements that have an+b-1 siblings before it in the document tree, for any positive integer or zero value of n, and has a parent element. For values of a and b greater than zero, this effectively divides the element's children into groups of a elements (the last group taking the remainder), and selecting the bth element of each group. For example, this allows the selectors to address every other row in a table, and could be used to alternate the color of paragraph text in a cycle of four. The a and b values must be integers (positive, negative, or zero). The index of the first child of an element is 1. In addition to this, :nth-child() can take odd and even as arguments instead. odd has the same signification as 2n+1, and even has the same signification as 2n.|tr:nth-child(2n+1) finds every odd row of a table. :nth-child(10n-1) the 9th, 19th, 29th, etc, element. li:nth-child(5) the 5h li|Y|Y
:nth-last-child(an+b)|elements that have an+b-1 siblings after it in the document tree. Otherwise like :nth-child()|tr:nth-last-child(-n+2) the last two rows of a table|Y|N
:nth-of-type(an+b)|pseudo-class notation represents an element that has an+b-1 siblings with the same expanded element name before it in the document tree, for any zero or positive integer value of n, and has a parent element|img:nth-of-type(2n+1)|Y|N
:nth-last-of-type(an+b)|pseudo-class notation represents an element that has an+b-1 siblings with the same expanded element name after it in the document tree, for any zero or positive integer value of n, and has a parent element|img:nth-last-of-type(2n+1)|Y|N
:first-child|elements that are the first child of some other element.|div > p:first-child|Y|Y
:last-child|elements that are the last child of some other element.|ol > li:last-child|Y|N
:first-of-type|elements that are the first sibling of its type in the list of children of its parent element|dl dt:first-of-type|Y|N
:last-of-type|elements that are the last sibling of its type in the list of children of its parent element|tr > td:last-of-type|Y|N
:only-child|elements that have a parent element and whose parent element hasve no other element children||Y|N
:only-of-type|an element that has a parent element and whose parent element has no other element children with the same expanded element name||Y|N
:empty|elements that have no children at all||Y|N
:before|execute actions before the tag that matches. Does not buffer|p:first-child:before| N* | Y
:after|execute actions after the tag that matches. Does not buffer|img:after| N* | Y
:start(n)|execute actions only when n matches has occurred. Defaults to 1|ul li:start(5)| N* | Y
:count(n)|execute actions at most n times. defaults to 0 (unlimited)|head > title:count(1)| N* | Y

* JSoup can perform equivalent functions using code while traversing the Document.
