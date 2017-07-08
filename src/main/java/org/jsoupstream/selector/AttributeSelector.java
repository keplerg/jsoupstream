package org.jsoupstream.selector;

/**
 *  Represents an attribute selector. (i.e. [{attribute-name}{attribute-comparator}{attribute-value}]
 */
public class AttributeSelector
{

    // The Comparator type
    public static enum ComparatorType
    {
        NONE,                   // no comparator
        EQUALS,                 // '='
        STARTS_WITH,            // '^='
        ENDS_WITH,              // '$='
        SUBSTRING,              // '*='
        CONTAINS,               // '~='
        CONTAINS_HYPHENATED     // '|='
    }

    private ComparatorType comparator;
    private String attribute_name;
    private String attribute_value;

    public AttributeSelector(String s, ComparatorType t, String v)
    {
        this.attribute_name = s;
        this.comparator = t;
        this.attribute_value = v;
    }

    public AttributeSelector(String s)
    {
        this.attribute_name = s;
        this.attribute_value = null;
        this.comparator = ComparatorType.NONE;
    }


    public ComparatorType getComparatorType()
    {
        return comparator;
    }

    public void setComparatorType( String c )
    {
        if ( c.equals( "=" ) )
        {
            comparator = ComparatorType.EQUALS;
        }
        else if ( c.equals( "^=" ) )
        {
            comparator = ComparatorType.STARTS_WITH;
        }
        else if ( c.equals( "$=" ) )
        {
            comparator = ComparatorType.ENDS_WITH;
        }
        else if ( c.equals( "*=" ) )
        {
            comparator = ComparatorType.SUBSTRING;
        }
        else if ( c.equals( "~=" ) )
        {
            comparator = ComparatorType.CONTAINS;
        }
        else if ( c.equals( "|=" ) )
        {
            comparator = ComparatorType.CONTAINS_HYPHENATED;
        }

        return;
    }

    public String getAttributeName()
    {
        return attribute_name;
    }

    public String getAttributeValue()
    {
        return attribute_value;
    }

    public void setAttributeName(String name)
    {
        attribute_name = name;
    }

    public void setAttributeValue(String value)
    {
        attribute_value = value;
    }

    public String toString()
    {
        if ( comparator == ComparatorType.NONE )
        {
            return "["+attribute_name+"]";
        }
        else
        {
            if ( attribute_name.equalsIgnoreCase( "class" ) && comparator == ComparatorType.CONTAINS )
            {
                return "."+attribute_value;
            }
            else if ( attribute_name.equalsIgnoreCase( "id" ) && comparator == ComparatorType.EQUALS )
            {
                return "#"+attribute_value;
            }
            else
            {
                StringBuffer sb = new StringBuffer( "[" );
                sb.append( attribute_name );
                switch ( comparator )
                {
                case EQUALS: sb.append( "=" ); break;
                case STARTS_WITH: sb.append( "^=" ); break;
                case ENDS_WITH: sb.append( "$=" ); break;
                case SUBSTRING: sb.append( "*=" ); break;
                case CONTAINS: sb.append( "~=" ); break;
                case CONTAINS_HYPHENATED: sb.append("|=" ); break;
                default: sb.append( "??" ); break;
                }
                sb.append( "\"" );
                sb.append( attribute_value );
                sb.append( "\"" );
                sb.append( "]" );

                return sb.toString();
            }
        }
    }
}
