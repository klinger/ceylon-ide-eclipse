package com.redhat.ceylon.eclipse.code.editor;

import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.ASTRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.AVERBATIM_STRING;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.CHAR_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.EOF;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.LINE_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.MULTI_COMMENT;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_END;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_LITERAL;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_MID;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.STRING_START;
import static com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer.VERBATIM_STRING;
import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getDefaultIndent;
import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getIndentSpaces;
import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getIndentWithSpaces;
import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.getPreferences;
import static com.redhat.ceylon.eclipse.code.editor.CeylonAutoEditStrategy.initialIndent;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLOSE_ANGLES;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLOSE_BACKTICKS;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLOSE_BRACES;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLOSE_BRACKETS;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLOSE_PARENS;
import static com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration.CLOSE_QUOTES;
import static com.redhat.ceylon.eclipse.code.parse.CeylonSourcePositionLocator.getTokenIndexAtCharacter;
import static java.lang.Character.isWhitespace;

import java.util.List;

import org.antlr.runtime.CommonToken;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

import com.redhat.ceylon.compiler.typechecker.parser.CeylonLexer;

class AutoEdit {
	
	public AutoEdit(IDocument document, List<CommonToken> tokens,
			DocumentCommand command) {
		this.document = document;
		this.tokens = tokens;
		this.command = command;
	}

	private IDocument document;
	private List<CommonToken> tokens;
	private DocumentCommand command;
	
    public void customizeDocumentCommand() {
    	
        //Note that IMP's Correct Indentation sends us a tab
    	//character at the start of each line of selected
    	//text. This is amazingly sucky because it's very
    	//difficult to distinguish Correct Indentation from
    	//an actual typed tab.
    	//Note also that typed tabs are replaced with spaces
    	//before this method is called if the spacesfortabs 
    	//setting is enabled.
        if (command.doit == false) {
            return;
        }
        
        //command.length>0 means we are replacing or deleting text
        else if (command.text!=null && command.length==0) { 
            if (command.text.isEmpty()) {
                //workaround for a really annoying bug where we 
                //get sent "" instead of "\t" or "    " by IMP
                //reconstruct what we would have been sent 
                //without the bug
                if (getIndentWithSpaces()) {
                    int overhang = getPrefix().length() % getIndentSpaces();
                    command.text = getDefaultIndent().substring(overhang);
                }
                else {
                	command.text = "\t";
                }
                smartIndentOnKeypress();
            }
            else if (command.text.length()==1 && isLineEnding(command.text)) {
            	//a typed newline
                smartIndentAfterNewline();
            }
            else if (command.text.length()==1 || 
                    //when spacesfortabs is enabled, we get 
                    //sent spaces instead of a tab
                    getIndentWithSpaces() && isIndent(getPrefix())) {
            	//anything that might represent a single 
                //keypress or a Correct Indentation
                smartIndentOnKeypress();
            }
        }
        
        closeOpening();
    }

	private static String[][] FENCES = {
			{ "'", "'", CLOSE_QUOTES },
			{ "\"", "\"", CLOSE_QUOTES },
			{ "`", "`", CLOSE_BACKTICKS },
			{ "<", ">", CLOSE_ANGLES },
			{ "(", ")", CLOSE_PARENS },
			{ "[", "]", CLOSE_BRACKETS }};

	public void closeOpening() {
		
		try {
			// TODO: improve this, check the surrounding token type!
			if (document.getChar(command.offset - 1) == '\\') {
				return;
			}
		} 
		catch (BadLocationException e) {}

		String current = command.text;
		String opening = null;
		String closing = null;
		
		boolean found=false;
		IPreferenceStore store = getPreferences();
		for (String[] type : FENCES) {
			if (type[0].equals(current) || 
					type[1].equals(current)) {
			    if (store==null || store.getBoolean(type[2])) {
			        opening = type[0];
			        closing = type[1];
			        found = true;
			        break;
			    }
			}
		}
		
		if (found) {
		
			if (current.equals(closing)) {
				//typed character is a closing fence
				try {
					// skip one ahead if next char is already a closing fence
					if (skipClosingFence(closing)) {
						command.text = "";
						command.shiftsCaret = false;
						command.caretOffset = command.offset + 1;
						return;
					}
				} 
				catch (BadLocationException e) {}
			}

            if (current.equals(opening) && (!isQuotedOrCommented(command.offset) || 
            		isGraveAccentCharacterInStringLiteral(command.offset, opening) ||
            		isOpeningBracketInAnnotationStringLiteral(command.offset, opening))) {
				//typed character is an opening fence
				if (closeOpeningFence(opening, closing)) {
					//add a closing fence
					command.shiftsCaret = false;
					command.caretOffset = command.offset + 1;
				    if (isGraveAccentCharacterInStringLiteral(command.offset, opening)) {
				        try {
				            if (command.offset>1 &&
				                    document.get(command.offset-1,1).equals("`") &&
				                    !document.get(command.offset-2,1).equals("`")) {
				            	command.text += "``";
				            }
				        } 
				        catch (BadLocationException e) {}
				    }
				    else if (isOpeningBracketInAnnotationStringLiteral(command.offset, opening)) {
                        try {
                            if (command.offset>1 &&
                                    document.get(command.offset-1,1).equals("[") &&
                                    !document.get(command.offset-2,1).equals("]")) {
                                command.text += "]]";
                            }
                        } 
                        catch (BadLocationException e) {}
				    }
				    else if (opening.equals("\"")) {
				        try {
				            if (command.offset<=1 ||
				                    !document.get(command.offset-2,1).equals("\"")) {
                                command.text += closing;
                            }
				            else if (command.offset>1 &&
				                    document.get(command.offset-2,2).equals("\"\"") &&
				                    !(command.offset>2 && document.get(command.offset-3,1).equals("\""))) {
				                command.text += "\"\"\"";
				            }
				        } 
				        catch (BadLocationException e) {}
				    }
				    else {
				        command.text += closing;
				    }
				}
			}

		}
	}

    private boolean skipClosingFence(String closing) throws BadLocationException {
        return String.valueOf(document.getChar(command.offset)).equals(closing);
    }

	private boolean closeOpeningFence(String opening, String closing) {
		boolean closeOpening;
		if (opening.equals(closing)) { 
			closeOpening = count(opening)%2==0;
		}
		else { 
			closeOpening = count(opening)>=count(closing);
		}

		if (opening.equals("<")) {
			// only close angle brackets if it's after a UIdentifier
			// if(a< -> don't close
			// if(Some< -> close
			// A< -> close
			int currOfset = command.offset - 1;
			char currChar;
			try {
				while (Character.isAlphabetic(currChar = document.getChar(currOfset))) {
					currOfset--;
				}
				currChar = document.getChar(currOfset + 1);
				if (!Character.isUpperCase(currChar)) {
					closeOpening = false;
				}
			} catch (BadLocationException e) {
			}
		}
		return closeOpening;
	}

	private String getPrefix() {
		try {
			int lineOffset = document.getLineInformationOfOffset(command.offset).getOffset();
			return document.get(lineOffset, command.offset-lineOffset) + command.text;
		} 
		catch (BadLocationException e) {
			return command.text;
		}
	}
    
    public boolean isIndent(String text) {
        if (!text.isEmpty() && 
        		text.length() % getIndentSpaces()==0) {
            for (char c: text.toCharArray()) {
                if (c!=' ') return false;
            }
            return true;
        }
        else {
            return false;
        }
    }
    
    private void smartIndentAfterNewline() {
        if (command.offset==-1 || document.getLength()==0) {
            return;
        }

        try {
            //if (end > start) {
                indentNewLine();
            //}
        } 
        catch (BadLocationException bleid ) {
            bleid.printStackTrace();
        }
    }

    private void smartIndentOnKeypress() {
        if (command.offset==-1 || document.getLength()==0) {
            return;
        }
         
        try {
            adjustIndentOfCurrentLine();
        }
        catch (BadLocationException ble) {
            ble.printStackTrace();
        }
    }
    
    private boolean isStringOrCommentContinuation(int offset) {
        int type = tokenType(offset);
        return type==STRING_LITERAL || 
        		type==STRING_MID ||
        		type==STRING_START ||
        		type==STRING_END ||
        		type==VERBATIM_STRING ||
        		type==ASTRING_LITERAL ||
        		type==AVERBATIM_STRING ||
        		type==MULTI_COMMENT;
    }

    private boolean isQuotedOrCommented(int offset) {
    	int type = tokenType(offset);
    	return type==STRING_LITERAL ||
    			type==CHAR_LITERAL || 
    			type==STRING_MID ||
    			type==STRING_START ||
    			type==STRING_END ||
    			type==VERBATIM_STRING ||
    			type==ASTRING_LITERAL ||
    			type==AVERBATIM_STRING ||
    			type==LINE_COMMENT ||
    			type==MULTI_COMMENT;
    }
    
    private boolean isMultilineCommented(int offset) {
    	int type = tokenType(offset);
    	return type==MULTI_COMMENT;
    }
    
    private boolean isGraveAccentCharacterInStringLiteral(int offset, String fence) {
        if ("`".equals(fence)) {
            int type = tokenType(offset);
            return type == STRING_LITERAL ||
                    type == STRING_START ||
                    type == STRING_END ||
                    type == STRING_MID;
        }
        return false;
    }

    private boolean isOpeningBracketInAnnotationStringLiteral(int offset, String fence) {
        if ("[".equals(fence)) {
            int type = tokenType(offset);
            //damn, AutoEdit can now no longer 
            //distinguish annotation strings :(
            return type == ASTRING_LITERAL ||
                    type == AVERBATIM_STRING;
        }
        return false;
    }
    
    private boolean isMultilineCommentStart(int offset, IDocument d) {
    	CommonToken token = token(offset);
    	if (token==null) return false;
        try {
			return token.getType()==MULTI_COMMENT && 
					!token.getText().endsWith("*/") &&
					d.getLineOfOffset(offset)+1==token.getLine();
		} 
        catch (BadLocationException e) {
			return false;
		}
    }

    private int tokenType(int offset) {
    	CommonToken token = token(offset);
		return token==null ? -1 : token.getType();
    }
    
    int getTokenType(int offset) {
		int tokenIndex = getTokenIndexAtCharacter(tokens, offset);
		if (tokenIndex>=0) {
			CommonToken token = tokens.get(tokenIndex);
			return token.getType();
		}
		return -1;
    }
    
	private CommonToken token(int offset) {
        List<CommonToken> tokens = getTokens();
		if (tokens!=null) {
    		if (tokens.size()>1) {
    			if (tokens.get(tokens.size()-1).getStartIndex()==offset) { //at very end of file
    				//check to see if last token is an
    				//unterminated string or comment
    			    //Note: ANTLR sometimes sends me 2 EOFs, 
    			    //      so do this:
    			    CommonToken token = null;
    			    for (int i=1; token==null || token.getType()==EOF; i++) {
    			        token = tokens.get(tokens.size()-i);
    			    }
    				int type = token==null ? -1 : token.getType();
    				if ((type==STRING_LITERAL ||
    						type==STRING_END ||
    						type==ASTRING_LITERAL) && 
    						(!token.getText().endsWith("\"") ||
    						token.getText().length()==1) ||
    						(type==VERBATIM_STRING || type==AVERBATIM_STRING) && 
    						(!token.getText().endsWith("\"\"\"")||
    						token.getText().length()==3) ||
    						(type==MULTI_COMMENT) && 
    						(!token.getText().endsWith("*/")||
    						token.getText().length()==2) ||
    						type==LINE_COMMENT) {
    					return token;
    				}
    			}
    			else {
    				int tokenIndex = getTokenIndexAtCharacter(tokens, offset);
    				if (tokenIndex>=0) {
    					CommonToken token = tokens.get(tokenIndex);
    					if (token.getStartIndex()<offset) {
    						return token;
    					}
    				}
    			}
    		}
        }
		return null;
	}

    /*private boolean isLineComment(int offset) {
        IEditorPart editor = Util.getCurrentEditor();
        if (editor instanceof CeylonEditor) {
            CeylonParseController pc = ((CeylonEditor) editor).getParseController();
            if (pc.getTokens()==null) return false;
            int tokenIndex = getTokenIndexAtCharacter(pc.getTokens(), offset);
            if (tokenIndex>=0) {
  	            CommonToken token = pc.getTokens().get(tokenIndex);
                return token!=null 
                		&& token.getType()==CeylonLexer.LINE_COMMENT
                		&& token.getStartIndex()<=offset;
            }
        }
        return false;
    }*/

	private int getStringIndent(int offset) {
		CommonToken token = token(offset);
		if (token!=null) {
			int type = token.getType();
			if ((type==STRING_LITERAL || 
					type==STRING_MID ||
					type==STRING_START ||
					type==STRING_END ||
					type==ASTRING_LITERAL ||
					type==VERBATIM_STRING ||
					type==AVERBATIM_STRING) &&
					token.getStartIndex()<offset) {
				return getStartOfQuotedText(token, type);
			}
		}
		return -1;
	}

	private int getStartOfQuotedText(CommonToken token, int type) {
		int quote = type==VERBATIM_STRING ||
				type==AVERBATIM_STRING ? 3 : 1;
		return token.getCharPositionInLine()+quote;
	}

    private void adjustIndentOfCurrentLine()
            throws BadLocationException {
        switch (command.text.charAt(0)) {
            case '}':
                reduceIndentOfCurrentLine();
                break;
            case '{':
            case '\t':
                if (isStringOrCommentContinuation(command.offset)) {
                    shiftToBeginningOfStringOrCommentContinuation();
                }
                else {
                    fixIndentOfCurrentLine();
                }
                break;
            default:
                //when spacesfortabs is enabled, we get sent spaces instead of a tab
                if (getIndentWithSpaces() && isIndent(getPrefix())) {
                    if (isStringOrCommentContinuation(command.offset)) {
                        shiftToBeginningOfStringOrCommentContinuation();
                    }
                    else {
                        fixIndentOfCurrentLine();
                    }
                }
        }
    }

    private void shiftToBeginningOfStringOrCommentContinuation()
            throws BadLocationException {
        //int start = getStartOfCurrentLine(d, command);
        int end = getEndOfCurrentLine();
        int loc = firstEndOfWhitespace(command.offset/*start*/, end);
        if (loc>command.offset) {
            command.length = 0;
            command.text = "";
            command.caretOffset = loc;
        }
    }
    
    private void indentNewLine()
            throws BadLocationException {
        int stringIndent = getStringIndent(command.offset);
        int start = getStartOfCurrentLine();
        if (stringIndent>=0) {
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<stringIndent; i++) {
                char ws = document.getChar(start+i)=='\t' ? 
                        '\t' : ' ';
                sb.append(ws);
            }
            command.text = command.text + sb.toString();
        }
        else {
            char lastNonWhitespaceChar = getPreviousNonWhitespaceCharacter(command.offset-1);
            char endOfLastLineChar = getPreviousNonWhitespaceCharacterInLine(command.offset-1);
            char startOfNewLineChar = getNextNonWhitespaceCharacterInLine(command.offset);

            //let's attempt to account for line ending comments in determining if it is a
            //continuation, but only by looking at the previous line
            //TODO: make this handle line ending comments further back
            char lastNonWhitespaceCharAccountingForComments = getLastNonWhitespaceCharacterInLine(start, command.offset);
            if (lastNonWhitespaceCharAccountingForComments!='\n') {
                lastNonWhitespaceChar = lastNonWhitespaceCharAccountingForComments;
                endOfLastLineChar = lastNonWhitespaceCharAccountingForComments;
            }
            
            StringBuilder buf = new StringBuilder(command.text);
            IPreferenceStore store = getPreferences();
            boolean closeBrace = store==null || 
                    store.getBoolean(CLOSE_BRACES) && 
                            count("{")>count("}");
            int end = getEndOfCurrentLine();
			appendIndent(command.offset, end, start, command.offset, 
                    startOfNewLineChar, endOfLastLineChar, lastNonWhitespaceChar, 
                    closeBrace, true, buf); //false, because otherwise it indents after annotations, which I guess we don't want
            if (buf.length()>2 && buf.charAt(buf.length()-1)=='}') {
                String hanging = document.get(command.offset, end-command.offset); //stuff after the { on the current line
                buf.insert(command.caretOffset-command.offset, hanging);
                command.length = hanging.length();
            }
            command.text = buf.toString();
            
        }
        if (isMultilineCommentStart(command.offset, document)) {
        	command.shiftsCaret=false;
        	command.caretOffset=command.offset+command.text.length();
        	command.text = command.text + command.text + "*/";
        }
    }
    
    int count(String token) {
    	int count = 0;
    	List<CommonToken> tokens = getTokens();
		for (CommonToken tok: tokens) {
			if (tok.getText().equals(token)) {
				count++;
			}
		}
    	return count;
    }

	protected List<CommonToken> getTokens() {
		return tokens;
	}
    
    private void fixIndentOfCurrentLine()
            throws BadLocationException {
        int start = getStartOfCurrentLine();
        int end = getEndOfCurrentLine();
        int endOfWs = firstEndOfWhitespace(start, end);
        if (endOfWs<end && isMultilineCommented(endOfWs+1)) {
        	command.doit = false;
        	command.offset=start;
        	command.text="";
        	command.length=0;
        	return;
        }
        if (command.offset<endOfWs || 
                command.offset==start && command.shiftsCaret==false) { //Test for IMP's "Correct Indent"
            int endOfPrev = getEndOfPreviousLine();
            int startOfPrev = getStartOfPreviousLine();
            char endOfLastLineChar = getLastNonWhitespaceCharacterInLine(startOfPrev, endOfPrev);
            char lastNonWhitespaceChar = endOfLastLineChar=='\n' ? 
                    getPreviousNonWhitespaceCharacter(startOfPrev) : endOfLastLineChar;
            char startOfCurrentLineChar = command.text.equals("{") ? 
                    '{' : getNextNonWhitespaceCharacter(start, end);
            
            StringBuilder buf = new StringBuilder();
            appendIndent(start, end, startOfPrev, endOfPrev, 
            		startOfCurrentLineChar, endOfLastLineChar,
                    lastNonWhitespaceChar, false, true, buf);
            if (command.text.equals("{")) {
                buf.append("{");
            }
            command.text = buf.toString();
            command.offset=start;
            command.length=endOfWs-start;
        }
    }

    private void appendIndent(int start, int end, int startOfPrev, int endOfPrev,
            char startOfCurrentLineChar, char endOfLastLineChar, char lastNonWhitespaceChar,
            boolean closeBraces, boolean correctContinuation, StringBuilder buf)
            		throws BadLocationException {
//        boolean isContinuation = startOfCurrentLineChar!='{' && startOfCurrentLineChar!='}' &&
//                lastNonWhitespaceChar!=';' && lastNonWhitespaceChar!='}' && lastNonWhitespaceChar!='{'
//                		 && endOfLastLineChar!='\n'; //oops, there's that silly null again!
    	int prevEnding = getTokenType(lastEndOfWhitespace(startOfPrev, endOfPrev));
    	int currStarting = getTokenType(firstEndOfWhitespace(start, end));
    	boolean isContinuation = isBinaryOperator(prevEnding)||
    			isBinaryOperator(currStarting)||
    			isInheritanceClause(currStarting);
        boolean isOpening = endOfLastLineChar=='{' && startOfCurrentLineChar!='}';
        boolean isClosing = startOfCurrentLineChar=='}' && endOfLastLineChar!='{';
        appendIndent(isContinuation, isOpening, isClosing, correctContinuation, 
                startOfPrev, endOfPrev, closeBraces, buf);
    }
    
    boolean isInheritanceClause(int tt) {
    	return tt==CeylonLexer.EXTENDS||
    			tt==CeylonLexer.CASE_TYPES||
    			tt==CeylonLexer.TYPE_CONSTRAINT||
    			tt==CeylonLexer.SATISFIES;
    }
    
    boolean isBinaryOperator(int tt) {
    	return tt==CeylonLexer.SPECIFY||
    			tt==CeylonLexer.ASSIGN||
    			tt==CeylonLexer.COMPUTE||
    			tt==CeylonLexer.NOT_EQUAL_OP||
    			tt==CeylonLexer.EQUAL_OP||
    			tt==CeylonLexer.IDENTICAL_OP||
    			tt==CeylonLexer.ADD_SPECIFY||
    			tt==CeylonLexer.SUBTRACT_SPECIFY||
    			tt==CeylonLexer.DIVIDE_SPECIFY||
    			tt==CeylonLexer.MULTIPLY_SPECIFY||
    			tt==CeylonLexer.OR_SPECIFY||
    			tt==CeylonLexer.AND_SPECIFY||
    			tt==CeylonLexer.COMPLEMENT_SPECIFY||
    			tt==CeylonLexer.UNION_SPECIFY||
    			tt==CeylonLexer.INTERSECT_SPECIFY||
    			tt==CeylonLexer.MEMBER_OP||
    			tt==CeylonLexer.SPREAD_OP||
    			tt==CeylonLexer.SAFE_MEMBER_OP||
    			tt==CeylonLexer.SUM_OP||
    			tt==CeylonLexer.COMPLEMENT_OP||
    			tt==CeylonLexer.DIFFERENCE_OP||
    			tt==CeylonLexer.QUOTIENT_OP||
    			tt==CeylonLexer.PRODUCT_OP||
    			tt==CeylonLexer.REMAINDER_OP||
    			tt==CeylonLexer.RANGE_OP||
    			tt==CeylonLexer.SEGMENT_OP||
    			tt==CeylonLexer.ENTRY_OP||
    			tt==CeylonLexer.UNION_OP||
    			tt==CeylonLexer.INTERSECTION_OP||
    			tt==CeylonLexer.AND_OP||
    			tt==CeylonLexer.OR_OP||
    			tt==CeylonLexer.POWER_OP||
    			tt==CeylonLexer.COMPARE_OP||
    			tt==CeylonLexer.LARGE_AS_OP||
    			tt==CeylonLexer.LARGER_OP||
    			tt==CeylonLexer.SMALL_AS_OP||
    			tt==CeylonLexer.SMALLER_OP||
    			tt==CeylonLexer.SCALE_OP;
    }

//    private void reduceIndent(DocumentCommand command) {
//        int spaces = getIndentSpaces();
//        if (endsWithSpaces(command.text, spaces)) {
//            command.text = command.text.substring(0, command.text.length()-spaces);
//        }
//        else if (command.text.endsWith("\t")) {
//            command.text = command.text.substring(0, command.text.length()-1);
//        }
//    }

    private void reduceIndentOfCurrentLine()
            throws BadLocationException {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(document.get(command.offset-spaces, spaces),spaces)) {
            command.offset = command.offset-spaces;
            command.length = spaces;
        }
        else if (document.get(command.offset-1,1).equals("\t")) {
            command.offset = command.offset-1;
            command.length = 1;
        }
    }

    private void decrementIndent(StringBuilder buf, String indent)
            throws BadLocationException {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(indent,spaces)) {
            buf.setLength(buf.length()-spaces);
        }
        else if (endsWithTab(indent)) {
            buf.setLength(buf.length()-1);
        }
    }

    private int getStartOfPreviousLine()
            throws BadLocationException {
        return getStartOfPreviousLine(command.offset);
    }

    private int getStartOfPreviousLine(int offset) 
            throws BadLocationException {
        int os;
        int line = document.getLineOfOffset(offset);
        if (line==0) return -1;
        do {
            os = document.getLineOffset(--line);
        }
        while (isStringOrCommentContinuation(os));
        return os;
    }
    
    /*private int getStartOfNextLine(IDocument d, int offset) 
            throws BadLocationException {
        return d.getLineOffset(d.getLineOfOffset(offset)+1);
    }*/
    
    private void appendIndent(boolean isContinuation, boolean isBeginning,
            boolean isEnding,  boolean correctContinuation, int start, int end,
            boolean closeBraces, StringBuilder buf) 
            		throws BadLocationException {
        String indent = getIndent(start, end, isContinuation&&!correctContinuation);
        buf.append(indent);
        if (isBeginning) {
            //increment the indent level
            incrementIndent(buf, indent);
            if (closeBraces) {
            	command.shiftsCaret=false;
            	command.caretOffset=command.offset+buf.length();
            	String newlineChar;
            	if (end<document.getLength()) {
            		newlineChar = document.get(end, 1);
            	}
            	else if (start>0) {
            		newlineChar = document.get(start-1, 1);
            	}
            	else {
            		newlineChar = System.lineSeparator(); //TODO: is this right?
            	}
				buf.append(newlineChar)
            		.append(indent)
            		.append('}');
            }
        }
        else if (isContinuation&&correctContinuation) {
            incrementIndent(buf, indent);
            incrementIndent(buf, indent);
        }
        if (isEnding) {
            decrementIndent(buf, indent);
            if (isContinuation) decrementIndent(buf, indent);
        }
    }

    private String getIndent(int start, int end, 
    		boolean isUncorrectedContinuation) 
            throws BadLocationException {
    	if (start<0||end<0) return "";
        if (!isUncorrectedContinuation) {
            while (start>0) {
                //We're searching for an earlier line whose 
                //immediately preceding line ends cleanly 
                //with a {, }, or ; or which itelf starts 
                //with a }. We will use that to infer the 
                //indent for the current line
                char startingChar = getNextNonWhitespaceCharacterInLine(start);
                if (startingChar=='}') break;
                int prevEnd = end;
                int prevStart = start;
                char prevEndingChar;
                do {
                    prevEnd = getEndOfPreviousLine(prevStart);
                    prevStart = getStartOfPreviousLine(prevStart);
                    prevEndingChar = getLastNonWhitespaceCharacterInLine(prevStart, prevEnd);
                }
                while (prevEndingChar=='\n' && prevStart>0); //skip blank lines when searching for previous line
                if (prevEndingChar==';' || prevEndingChar=='{' || prevEndingChar=='}') break;
                end = prevEnd;
                start = prevStart;
            }
        }
        while (isStringOrCommentContinuation(start)) {
            end = getEndOfPreviousLine(start);
            start = getStartOfPreviousLine(start);
        }
        int endOfWs = firstEndOfWhitespace(start, end);
        return document.get(start, endOfWs-start);
    }

    private char getLastNonWhitespaceCharacterInLine(int offset, int end) 
            throws BadLocationException {
        char result = '\n'; //ahem, ugly null!
        int commentDepth=0;
        for (;offset<end; offset++) {
            char ch = document.getChar(offset);
            char next = document.getLength()>offset+1 ? 
                    document.getChar(offset+1) : '\n'; //another ugly null
            if (commentDepth==0) {
                if (ch=='/') {
                    if (next=='*') {
                        commentDepth++;
                    }
                    else if  (next=='/') {
                        return result;
                    }
                }
                else if (!isWhitespace(ch)) {
                    result=ch;
                }
            }
            else {
                if (ch=='*' && next=='/') {
                    commentDepth--;
                }
            }
        }
        return result;
    }
    
    private void incrementIndent(StringBuilder buf, String indent) {
        int spaces = getIndentSpaces();
        if (endsWithSpaces(indent,spaces)) {
            for (int i=1; i<=spaces; i++) {
                buf.append(' ');                            
            }
        }
        else if (endsWithTab(indent)) {
            buf.append('\t');
        }
        else {
        	initialIndent(buf);
        }
    }

    private boolean endsWithTab(String indent) {
        return !indent.isEmpty() &&
                indent.charAt(indent.length()-1)=='\t';
    }
    
    private char getPreviousNonWhitespaceCharacter(int offset)
            throws BadLocationException {
        for (;offset>=0; offset--) {
            String ch = document.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
            	!isQuotedOrCommented(offset)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private char getPreviousNonWhitespaceCharacterInLine(int offset)
            throws BadLocationException {
        //TODO: handle end-of-line comments
        for (;offset>=0; offset--) {
            String ch = document.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
            	!isQuotedOrCommented(offset) ||
                    isLineEnding(ch)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private char getNextNonWhitespaceCharacterInLine(int offset)
            throws BadLocationException {
        for (;offset<document.getLength(); offset++) {
            String ch = document.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
                !isQuotedOrCommented(offset) ||
                    isLineEnding(ch)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private char getNextNonWhitespaceCharacter(int offset, int end)
            throws BadLocationException {
        for (;offset<end; offset++) {
            String ch = document.get(offset,1);
            if (!isWhitespace(ch.charAt(0)) && 
                !isQuotedOrCommented(offset)) {
                return ch.charAt(0);
            }
        }
        return '\n';
    }

    private int getStartOfCurrentLine() 
            throws BadLocationException {
        int p = command.offset == document.getLength() ? command.offset-1 : command.offset;
        return document.getLineInformationOfOffset(p).getOffset();
    }
    
    private int getEndOfCurrentLine() 
            throws BadLocationException {
        int p = command.offset == document.getLength() ? command.offset-1 : command.offset;
        IRegion lineInfo = document.getLineInformationOfOffset(p);
        return lineInfo.getOffset() + lineInfo.getLength();
    }
    
    private int getEndOfPreviousLine() 
            throws BadLocationException {
        return getEndOfPreviousLine(command.offset);
    }

    private int getEndOfPreviousLine(int offset) 
            throws BadLocationException {
    	if (document.getLineOfOffset(offset)==0) return -1;
        int p = offset == document.getLength() ? offset-1 : offset;
        IRegion lineInfo = document.getLineInformation(document.getLineOfOffset(p)-1);
        return lineInfo.getOffset() + lineInfo.getLength();
    }
    
    private boolean endsWithSpaces(String string, int spaces) {
        if (string.length()<spaces) return false;
        for (int i=1; i<=spaces; i++) {
            if (string.charAt(string.length()-i)!=' ') {
                return false;
            }
        }
        return true;
    }
 
    /**
     * Returns the first offset greater than <code>offset</code> and smaller than
     * <code>end</code> whose character is not a space or tab character. If no such
     * offset is found, <code>end</code> is returned.
     *
     * @param d the document to search in
     * @param offset the offset at which searching start
     * @param end the offset at which searching stops
     * @return the offset in the specified range whose character is not a space or tab
     * @exception BadLocationException if position is an invalid range in the given document
     */
    private int firstEndOfWhitespace(int offset, int end)
            throws BadLocationException {
        while (offset < end) {
            char ch= document.getChar(offset);
            if (ch!=' ' && ch!='\t') {
                return offset;
            }
            offset++;
        }
        return end;
    }

    private int lastEndOfWhitespace(int offset, int end)
            throws BadLocationException {
    	end--;
        while (end > offset) {
            char ch= document.getChar(end);
            if (ch!=' ' && ch!='\t') {
                return end;
            }
            end--;
        }
        return offset;
    }

    private boolean isLineEnding(String text) {
        String[] delimiters = document.getLegalLineDelimiters();
        if (delimiters != null) {
            return TextUtilities.endsWith(delimiters, text)!=-1;
        }
        return false;
    }
    
}