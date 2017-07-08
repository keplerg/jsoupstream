package org.jsoupstream;

import java.util.List;
import java.util.ListIterator;

/**
   Set of generic callback functions to call as CSS actions. 
 */
public class Functions
{
    public Functions() {}

    public boolean delete(List<HtmlToken> tokenQueue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            lit.remove();
            HtmlToken.relinquish( token );
        }

        return true;
    }

    public boolean print(List<HtmlToken> tokenQueue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        System.out.print( "[[" );
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            System.out.print( new String( token.text ) );
        }
        System.out.println( "]]" );

        return true;
    }

    public boolean printErr(List<HtmlToken> tokenQueue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        System.err.print( "[[" );
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            System.err.print( new String( token.text ) );
        }
        System.err.println( "]]" );

        return true;
    }

    public boolean replaceAttribute(List<HtmlToken> tokenQueue, String attr, String newAttr)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            if ( token.type == HtmlToken.Type.ATTRIBUTE_NAME )
            {
                if ( attr.equalsIgnoreCase( new String( token.text ) ) )
                {
                    lit.set( HtmlToken.getToken( newAttr.getBytes(), HtmlToken.Type.ATTRIBUTE_NAME ) );
                    HtmlToken.relinquish( token );
                    return true;
                }
            }
        }

        return false;
    }

    public boolean addAttributeValue(List<HtmlToken> tokenQueue, String attr, String addValue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        boolean found = false;
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            if ( token.type == HtmlToken.Type.ATTRIBUTE_NAME )
            {
                if ( attr.equalsIgnoreCase( new String( token.text ) ) )
                {
                    found = true;
                }
            }
            else if ( found )
            {
                if ( token.type == HtmlToken.Type.ATTRIBUTE_VALUE )
                {
                    StringBuffer newValue = new StringBuffer( );
                    String value = new String( token.text );
                    char quote = value.charAt( 0 );
                    String arr[] = value.substring( 1, value.length() - 1 ).split( " +" );
                    boolean first = true;
    
                    newValue.append( quote );
                    for ( String val : arr )
                    {
                        if ( ! val.equals( addValue ) )
                        {
                            if ( !first )
                            {
                                newValue.append( " " );
                            }
                            first = false;

                            newValue.append( val );
                        }
                    }
                    newValue.append( " " );
                    newValue.append( addValue );
                    newValue.append( quote );
                    lit.set( HtmlToken.getToken( newValue.toString().getBytes(), HtmlToken.Type.ATTRIBUTE_VALUE ) );
                    HtmlToken.relinquish( token );
                    return true;
                }
                else if ( token.type != HtmlToken.Type.WHITESPACE && token.type != HtmlToken.Type.EQUALS )
                {
                    found = false;
                }
            }
        }

        return false;
    }

    public boolean replaceText(List<HtmlToken> tokenQueue, String text)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        boolean replaced = false;
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                if ( ! replaced )
                {
                    lit.set( HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT ) );
                    replaced = true;
                }
                else
                {
                    lit.remove();
                }
                HtmlToken.relinquish( token );
            }
        }

        return true;
    }

    public boolean replaceInner(List<HtmlToken> tokenQueue, String text)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        boolean replaced = false;
        boolean started = false;
        int count = 0;
        int size = 0;
        HtmlToken token;

        size = tokenQueue.size() - 3;
        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( started && count < size )
            {
                if ( ! replaced )
                {
                    lit.set( HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT ) );
                    replaced = true;
                }
                else
                {
                    lit.remove();
                }
                HtmlToken.relinquish( token );
            }
            count++;
            if ( token.type == HtmlToken.Type.CLOSE_TAG )
            {
                started = true;
            }
        }

        return true;
    }

    public boolean wrapElement(List<HtmlToken> tokenQueue, String tag)
    {
        tokenQueue.add( 0, HtmlToken.getToken( ">".getBytes(), HtmlToken.Type.CLOSE_TAG ) );
        tokenQueue.add( 0, HtmlToken.getToken( tag.getBytes(), HtmlToken.Type.TAG_NAME ) );
        tokenQueue.add( 0, HtmlToken.getToken( "<".getBytes(), HtmlToken.Type.OPEN_TAG ) );
        tokenQueue.add( HtmlToken.getToken( "</".getBytes(), HtmlToken.Type.OPEN_END_TAG ) );
        tokenQueue.add( HtmlToken.getToken( tag.getBytes(), HtmlToken.Type.TAG_NAME ) );
        tokenQueue.add( HtmlToken.getToken( ">".getBytes(), HtmlToken.Type.CLOSE_TAG ) );

        return true;
    }

    public boolean addAttribute(List<HtmlToken> tokenQueue, String name, String value)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TAG_NAME )
            {
                lit.add( HtmlToken.getToken( " ".getBytes(), HtmlToken.Type.WHITESPACE ) );
                lit.add( HtmlToken.getToken( name.getBytes(), HtmlToken.Type.ATTRIBUTE_NAME ) );
                lit.add( HtmlToken.getToken( "=".getBytes(), HtmlToken.Type.EQUALS ) );
                lit.add( HtmlToken.getToken( ("\""+value+"\"").getBytes(), HtmlToken.Type.ATTRIBUTE_VALUE ) );
                break;
            }
        }

        return true;
    }

    public boolean insertBefore(List<HtmlToken> tokenQueue, String text)
    {
        HtmlToken token = HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT );
        tokenQueue.add( 0 , token );

        return true;
    }

    public boolean insertAfter(List<HtmlToken> tokenQueue, String text)
    {
        HtmlToken token = HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT );
        tokenQueue.add( token );

        return true;
    }
}
