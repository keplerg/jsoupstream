package org.jsoupstream.example;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import org.jsoupstream.HtmlParser;
import org.jsoupstream.HtmlLexer;

public class TestParser {

	public static void main(String[] args) {
		InputStream css_is = null;
		InputStream html_is = null;
		char c = (char)0;
		int count = 1;
		int line = 1;

		try
		{
            if ( args.length > 2 )
            {
                count = Integer.parseInt( args[2] );
            }

            // parse the CSS and create a parser 1 time only
            css_is = new FileInputStream(args[0]);
            HtmlParser parser = new HtmlParser(css_is);
            css_is.close();

            if ( args.length > 3 )
            {
                if ( args[3].startsWith( "min" ) || args[3].equalsIgnoreCase( "yes" ) )
                {
                    parser.setMinimizeHtml( true );
                }
            }

            for (int i = 0; i < count; i++)
            {
                if ( args[1].startsWith( "http" ) )
                {
                    URL url = new URL( args[1] );
                    html_is = url.openStream();
                }
                else
                {
                    html_is = new FileInputStream( args[1] );
                }

                HtmlLexer lexer = new HtmlLexer( html_is );
                String result = parser.parse( lexer );
                html_is.close();
                System.out.print( result );

                // parser must be reset if it to be reused
                parser.reset();
            }
		} catch(Exception e) {
			// if any I/O error occurs
			System.err.println("ERROR: "+e.getMessage());
			e.printStackTrace();
		}
	}
}
