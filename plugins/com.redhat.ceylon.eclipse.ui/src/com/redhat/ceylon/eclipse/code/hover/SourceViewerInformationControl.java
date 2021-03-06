package com.redhat.ceylon.eclipse.code.hover;

import static com.redhat.ceylon.eclipse.code.editor.Navigation.getNodePath;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getEditorInput;
import static com.redhat.ceylon.eclipse.util.Nodes.findNode;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedNode;
import static org.eclipse.jdt.internal.ui.JavaPluginImages.setLocalImageDescriptors;
import static org.eclipse.jdt.ui.PreferenceConstants.EDITOR_SOURCE_HOVER_BACKGROUND_COLOR;
import static org.eclipse.jdt.ui.PreferenceConstants.EDITOR_SOURCE_HOVER_BACKGROUND_COLOR_SYSTEM_DEFAULT;
import static org.eclipse.ui.texteditor.AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND;
import static org.eclipse.ui.texteditor.AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND_SYSTEM_DEFAULT;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IJavaColorConstants;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension;
import org.eclipse.jface.text.IInformationControlExtension2;
import org.eclipse.jface.text.IInformationControlExtension3;
import org.eclipse.jface.text.IInformationControlExtension5;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewer;
import com.redhat.ceylon.eclipse.code.editor.CeylonSourceViewerConfiguration;
import com.redhat.ceylon.eclipse.code.editor.Navigation;
import com.redhat.ceylon.eclipse.code.editor.SourceArchiveDocumentProvider;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

/**
 * Source viewer based implementation of <code>IInformationControl</code>.
 * Displays information in a source viewer.
 *
 * @since 3.0
 */
public class SourceViewerInformationControl 
    implements IInformationControl, IInformationControlExtension,
               IInformationControlExtension2,
               IInformationControlExtension3, IInformationControlExtension5, 
               DisposeListener {
    
    private final CeylonEditor editor;
    private Node referencedNode;
    private final CeylonParseController parseController = 
            new CeylonParseController();
    private final IDocumentProvider docProvider = 
            new SourceArchiveDocumentProvider();
    private IEditorInput ei;
    
	/** The control's shell */
	private Shell fShell;
	/** The control's text widget */
	private StyledText fText;
	/** The text font (do not dispose!) */
	private Font fTextFont;
	/** The control's source viewer */
	private SourceViewer fViewer;
	/**
	 * The optional status field.
	 *
	 * @since 3.0
	 */
	private Label fStatusField;
	/**
	 * The separator for the optional status field.
	 *
	 * @since 3.0
	 */
//	private Label fSeparator;
	/**
	 * The font of the optional status text label.
	 *
	 * @since 3.0
	 */
	private Font fStatusTextFont;
	/**
	 * The color of the optional status text label or <code>null</code> if none.
	 * 
	 * @since 3.6
	 */
	private Color fStatusTextForegroundColor;
	/**
	 * The width size constraint.
	 * @since 3.2
	 */
	private int fMaxWidth= SWT.DEFAULT;
	/**
	 * The height size constraint.
	 * @since 3.2
	 */
	private int fMaxHeight= SWT.DEFAULT;
	/**
	 * The orientation of the shell
	 * @since 3.4
	 */
	private final int fOrientation;

	private Color fBackgroundColor;
	private boolean fIsSystemBackgroundColor= true;
    private int fResizeHandleSize;

    public SourceViewerInformationControl(CeylonEditor editor, 
            Shell parent, boolean isResizable, int orientation, 
            String statusFieldText) {
        this(editor, parent, isResizable, orientation, statusFieldText, null);
    }
    
    public SourceViewerInformationControl(CeylonEditor editor, 
            Shell parent, boolean isResizable, int orientation, 
            ToolBarManager toolBarManager) {
        this(editor, parent, isResizable, orientation, null, toolBarManager);
    }

	/**
	 * Creates a source viewer information control with the given shell as parent. The given
	 * styles are applied to the created styled text widget. The status field will
	 * contain the given text or be hidden.
	 *
	 * @param parent the parent shell
	 * @param isResizable <code>true</code> if resizable
	 * @param orientation the orientation
	 * @param statusFieldText the text to be used in the optional status field
	 *            or <code>null</code> if the status field should be hidden
	 */
	private SourceViewerInformationControl(CeylonEditor editor, 
	        Shell parent, boolean isResizable, int orientation, 
	        String statusFieldText, ToolBarManager toolBarManager) {
		this.editor = editor;
        Assert.isLegal(orientation == SWT.RIGHT_TO_LEFT || 
                orientation == SWT.LEFT_TO_RIGHT || 
                orientation == SWT.NONE);
		fOrientation= orientation;
		
		fResizeHandleSize= -1;
		
		GridLayout layout;
		GridData gd;

		int shellStyle= 
		        SWT.TOOL | SWT.ON_TOP | orientation | 
		        (isResizable ? SWT.RESIZE : 0);
		int textStyle= isResizable ? 
		        SWT.V_SCROLL | SWT.H_SCROLL : SWT.NONE;

		fShell= new Shell(parent, SWT.NO_FOCUS | SWT.ON_TOP | shellStyle);
		Display display= fShell.getDisplay();

		initializeColors();

		Composite composite= fShell;
		layout= new GridLayout(1, false);
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		composite.setLayout(layout);
		gd= new GridData(GridData.FILL_HORIZONTAL);
		composite.setLayoutData(gd);

		if (statusFieldText!=null || toolBarManager!=null) {
			composite= new Composite(composite, SWT.NONE);
			layout= new GridLayout(1, false);
			layout.marginHeight= 0;
			layout.marginWidth= 0;
			layout.verticalSpacing= 1;
			composite.setLayout(layout);
			gd= new GridData(GridData.FILL_BOTH);
			composite.setLayoutData(gd);
			composite.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
			composite.setBackground(fBackgroundColor);
		}

		// Source viewer
		fViewer= new CeylonSourceViewer(editor, composite, null, null, false, textStyle);
		fViewer.configure(new CeylonSourceViewerConfiguration(editor) {
	        @Override
	        protected CeylonParseController getParseController() {
	            return parseController;
	        }
	    });
		fViewer.setEditable(false);

		fText= fViewer.getTextWidget();
		gd= new GridData(GridData.BEGINNING | GridData.FILL_BOTH);
		fText.setLayoutData(gd);
		fText.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
		fText.setBackground(fBackgroundColor);

		initializeFont();

		fText.addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent e)  {
				if (e.character == 0x1B) // ESC
					fShell.dispose();
			}
			public void keyReleased(KeyEvent e) {}
		});

		// Status field
		if (statusFieldText!=null) {
			createStatusLabel(statusFieldText, composite);
		}
		if (toolBarManager!=null) {
		    createToolBar(toolBarManager, composite);
		}

		addDisposeListener(this);
	}

    private void createStatusLabel(String statusFieldText, Composite composite) {
        // Horizontal separator line
//			fSeparator= new Label(composite, 
//			        SWT.SEPARATOR | SWT.HORIZONTAL | SWT.LINE_DOT);
//			fSeparator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // Status field label
        fStatusField= new Label(composite, SWT.RIGHT);
        fStatusField.setText(statusFieldText);
        Font font= fStatusField.getFont();
        FontData[] fontDatas= font.getFontData();
        for (int i= 0; i < fontDatas.length; i++)
        	fontDatas[i].setHeight(fontDatas[i].getHeight() * 9 / 10);
        fStatusTextFont= new Font(fStatusField.getDisplay(), fontDatas);
        fStatusField.setFont(fStatusTextFont);
        GridData gd2= new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
        fStatusField.setLayoutData(gd2);
        
        RGB javaDefaultColor= 
                JavaUI.getColorManager().getColor(IJavaColorConstants.JAVA_DEFAULT).getRGB();
        fStatusTextForegroundColor= 
                new Color(fStatusField.getDisplay(), 
                        blend(fBackgroundColor.getRGB(), javaDefaultColor, 0.56f));
        fStatusField.setForeground(fStatusTextForegroundColor);
        fStatusField.setBackground(fBackgroundColor);
    }
	
    private void createToolBar(ToolBarManager toolBarManager, Composite composite) {
        final Composite bars= new Composite(composite, SWT.NONE);
        bars.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

        GridLayout layout= new GridLayout(3, false);
        layout.marginHeight= 0;
        layout.marginWidth= 0;
        layout.horizontalSpacing= 0;
        layout.verticalSpacing= 0;
        bars.setLayout(layout);

        ToolBar toolBar= toolBarManager.createControl(bars);
        GridData gd= new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false);
        toolBar.setLayoutData(gd);

        Composite spacer= new Composite(bars, SWT.NONE);
        gd= new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.widthHint= 0;
        gd.heightHint= 0;
        spacer.setLayoutData(gd);

        addMoveSupport(spacer);
        addResizeSupportIfNecessary(bars);
    }

    private void addResizeSupportIfNecessary(final Composite bars) {
        // XXX: workarounds for
        // - https://bugs.eclipse.org/bugs/show_bug.cgi?id=219139 : API to add resize grip / grow box in lower right corner of shell
        // - https://bugs.eclipse.org/bugs/show_bug.cgi?id=23980 : platform specific shell resize behavior
        String platform= SWT.getPlatform();
        final boolean isWin= platform.equals("win32"); //$NON-NLS-1$
        if (!isWin && !platform.equals("gtk")) //$NON-NLS-1$
            return;

        final Canvas resizer= new Canvas(bars, SWT.NONE);

        int size= getResizeHandleSize(bars);

        GridData data= new GridData(SWT.END, SWT.END, false, true);
        data.widthHint= size;
        data.heightHint= size;
        resizer.setLayoutData(data);
        resizer.addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                Point s= resizer.getSize();
                int x= s.x - 2;
                int y= s.y - 2;
                int min= Math.min(x, y);
                if (isWin) {
                    // draw dots
                    e.gc.setBackground(resizer.getDisplay().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
                    int end= min - 1;
                    for (int i= 0; i <= 2; i++)
                        for (int j= 0; j <= 2 - i; j++)
                            e.gc.fillRectangle(end - 4 * i, end - 4 * j, 2, 2);
                    end--;
                    e.gc.setBackground(resizer.getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
                    for (int i= 0; i <= 2; i++)
                        for (int j= 0; j <= 2 - i; j++)
                            e.gc.fillRectangle(end - 4 * i, end - 4 * j, 2, 2);

                } else {
                    // draw diagonal lines
                    e.gc.setForeground(resizer.getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
                    for (int i= 1; i < min; i+= 4) {
                        e.gc.drawLine(i, y, x, i);
                    }
                    e.gc.setForeground(resizer.getDisplay().getSystemColor(SWT.COLOR_WIDGET_HIGHLIGHT_SHADOW));
                    for (int i= 2; i < min; i+= 4) {
                        e.gc.drawLine(i, y, x, i);
                    }
                }
            }
        });

        final boolean isRTL= (resizer.getShell().getStyle() & SWT.RIGHT_TO_LEFT) != 0;
        resizer.setCursor(resizer.getDisplay().getSystemCursor(isRTL ? SWT.CURSOR_SIZESW : SWT.CURSOR_SIZESE));
        MouseAdapter resizeSupport= new MouseAdapter() {
            private MouseMoveListener fResizeListener;

            public void mouseDown(MouseEvent e) {
                Rectangle shellBounds= fShell.getBounds();
                final int shellX= shellBounds.x;
                final int shellY= shellBounds.y;
                final int shellWidth= shellBounds.width;
                final int shellHeight= shellBounds.height;
                Point mouseLoc= resizer.toDisplay(e.x, e.y);
                final int mouseX= mouseLoc.x;
                final int mouseY= mouseLoc.y;
                fResizeListener= new MouseMoveListener() {
                    public void mouseMove(MouseEvent e2) {
                        Point mouseLoc2= resizer.toDisplay(e2.x, e2.y);
                        int dx= mouseLoc2.x - mouseX;
                        int dy= mouseLoc2.y - mouseY;
                        if (isRTL) {
                            setLocation(new Point(shellX + dx, shellY));
                            setSize(shellWidth - dx, shellHeight + dy);
                        } else {
                            setSize(shellWidth + dx, shellHeight + dy);
                        }
                    }
                };
                resizer.addMouseMoveListener(fResizeListener);
            }

            public void mouseUp(MouseEvent e) {
                resizer.removeMouseMoveListener(fResizeListener);
                fResizeListener= null;
            }
        };
        resizer.addMouseListener(resizeSupport);
    }

    private int getResizeHandleSize(Composite parent) {
        if (fResizeHandleSize == -1) {
            Slider sliderV= new Slider(parent, SWT.VERTICAL);
            Slider sliderH= new Slider(parent, SWT.HORIZONTAL);
            int width= sliderV.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
            int height= sliderH.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
            sliderV.dispose();
            sliderH.dispose();
            fResizeHandleSize= Math.min(width, height);
        }

        return fResizeHandleSize;
    }

    /**
     * Adds support to move the shell by dragging the given control.
     *
     * @param control the control that can be used to move the shell
     */
    private void addMoveSupport(final Control control) {
        MouseAdapter moveSupport= new MouseAdapter() {
            private MouseMoveListener fMoveListener;

            public void mouseDown(MouseEvent e) {
                Point shellLoc= fShell.getLocation();
                final int shellX= shellLoc.x;
                final int shellY= shellLoc.y;
                Point mouseLoc= control.toDisplay(e.x, e.y);
                final int mouseX= mouseLoc.x;
                final int mouseY= mouseLoc.y;
                fMoveListener= new MouseMoveListener() {
                    public void mouseMove(MouseEvent e2) {
                        Point mouseLoc2= control.toDisplay(e2.x, e2.y);
                        int dx= mouseLoc2.x - mouseX;
                        int dy= mouseLoc2.y - mouseY;
                        fShell.setLocation(shellX + dx, shellY + dy);
                    }
                };
                control.addMouseMoveListener(fMoveListener);
            }

            public void mouseUp(MouseEvent e) {
                control.removeMouseMoveListener(fMoveListener);
                fMoveListener= null;
            }
        };
        control.addMouseListener(moveSupport);
    }


	/**
	 * Returns an RGB that lies between the given foreground and background
	 * colors using the given mixing factor. A <code>factor</code> of 1.0 will produce a
	 * color equal to <code>fg</code>, while a <code>factor</code> of 0.0 will produce one
	 * equal to <code>bg</code>.
	 * @param bg the background color
	 * @param fg the foreground color
	 * @param factor the mixing factor, must be in [0,&nbsp;1]
	 *
	 * @return the interpolated color
	 * @since 3.6
	 */
	private static RGB blend(RGB bg, RGB fg, float factor) {
		// copy of org.eclipse.jface.internal.text.revisions.Colors#blend(..)
		Assert.isLegal(bg != null);
		Assert.isLegal(fg != null);
		Assert.isLegal(factor >= 0f && factor <= 1f);
		
		float complement= 1f - factor;
		return new RGB(
				(int) (complement * bg.red + factor * fg.red),
				(int) (complement * bg.green + factor * fg.green),
				(int) (complement * bg.blue + factor * fg.blue)
		);
	}
	
	private void initializeColors() {
		IPreferenceStore store= 
		        JavaPlugin.getDefault().getPreferenceStore();
		RGB bgRGB;
		if (store.getBoolean(EDITOR_SOURCE_HOVER_BACKGROUND_COLOR_SYSTEM_DEFAULT)) {
			bgRGB= getVisibleBackgroundColor(fShell.getDisplay());
		} else {
			bgRGB= PreferenceConverter.getColor(store, 
			        EDITOR_SOURCE_HOVER_BACKGROUND_COLOR);
		}
		if (bgRGB != null) {
			fBackgroundColor= new Color(fShell.getDisplay(), bgRGB);
			fIsSystemBackgroundColor= false;
		} else {
			fBackgroundColor= fShell.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
			fIsSystemBackgroundColor= true;
		}
	}

	/**
	 * Returns <code>null</code> if {@link SWT#COLOR_INFO_BACKGROUND} is visibly distinct from the
	 * default Java source text color. Otherwise, returns the editor background color.
	 * 
	 * @param display the display
	 * @return an RGB or <code>null</code>
	 * @since 3.6.1
	 */
	public static RGB getVisibleBackgroundColor(Display display) {
		float[] infoBgHSB= 
		        display.getSystemColor(SWT.COLOR_INFO_BACKGROUND)
		                .getRGB().getHSB();
		
		Color javaDefaultColor= 
		        JavaUI.getColorManager().getColor(IJavaColorConstants.JAVA_DEFAULT);
		RGB javaDefaultRGB= javaDefaultColor != null ? 
		        javaDefaultColor.getRGB() : 
		            new RGB(255, 255, 255);
		float[] javaDefaultHSB= javaDefaultRGB.getHSB();
		
		if (Math.abs(infoBgHSB[2] - javaDefaultHSB[2]) < 0.5f) {
			// workaround for dark tooltip background color, see https://bugs.eclipse.org/309334
			IPreferenceStore preferenceStore= 
			        JavaPlugin.getDefault().getCombinedPreferenceStore();
			boolean useDefault= 
			        preferenceStore.getBoolean(PREFERENCE_COLOR_BACKGROUND_SYSTEM_DEFAULT);
			if (useDefault)
				return display.getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB();
			return PreferenceConverter.getColor(preferenceStore, 
			        PREFERENCE_COLOR_BACKGROUND);
		}
		return null;
	}

	/**
	 * Initialize the font to the Java editor font.
	 *
	 * @since 3.2
	 */
	private void initializeFont() {
		fTextFont= JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT);
		StyledText styledText= getViewer().getTextWidget();
		styledText.setFont(fTextFont);
	}
	
	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension2#setInput(java.lang.Object)
	 */
    @Override
    public void setInput(Object input) {
        if (input==null) return;
        IRegion hoverRegion = (IRegion) input;
        CeylonParseController controller = 
                editor.getParseController();
        int offset = hoverRegion.getOffset();
        int length = hoverRegion.getLength();
        Tree.CompilationUnit rootNode = 
                controller.getRootNode();
        referencedNode = 
                getReferencedNode(findNode(rootNode, 
                        offset, offset+length));
        if (referencedNode==null) return;
        IProject project = controller.getProject();
        IPath path = getNodePath(referencedNode);
        //CeylonParseController treats files with full paths subtly
        //differently to files with relative paths, so make the
        //path relative
        IPath pathToCompare = path;
        if (project!=null && 
                project.getLocation().isPrefixOf(path)) {
            pathToCompare = 
                    path.makeRelativeTo(project.getLocation());
        }
        IDocument doc;
        if (pathToCompare.equals(controller.getPath())) {
            doc = controller.getDocument();
        }
        else {
            ei = getEditorInput(referencedNode.getUnit());
            if (ei == null) {
                ei = getEditorInput(path);
            }
            try {
                docProvider.connect(ei);
                doc = docProvider.getDocument(ei);
            } 
            catch (CoreException e) {
                e.printStackTrace();
                return;
            }
        }
        fViewer.setDocument(doc);
        try {
            IRegion firstLine = 
                    doc.getLineInformationOfOffset(referencedNode.getStartIndex());
            IRegion lastLine = 
                    doc.getLineInformationOfOffset(referencedNode.getStopIndex());
            fViewer.setVisibleRegion(firstLine.getOffset(), 
                    lastLine.getOffset()+lastLine.getLength()-firstLine.getOffset());
        }
        catch (BadLocationException e) {
            e.printStackTrace();
        }
        parseController.initialize(path, project, null);
        parseController.parse(doc, new NullProgressMonitor(), null);
    }


	/*
	 * @see IInformationControl#setInformation(String)
	 */
	public void setInformation(String content) {
		if (content == null) {
			fViewer.setInput(null);
			return;
		}

		IDocument doc= new Document(content);
		JavaPlugin.getDefault().getJavaTextTools()
		        .setupJavaDocumentPartitioner(doc, 
		                IJavaPartitions.JAVA_PARTITIONING);
		fViewer.setInput(doc);
	}

	/*
	 * @see IInformationControl#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
			fShell.setVisible(visible);
	}

	/**
	 * {@inheritDoc}
	 * @since 3.0
	 */
	public void widgetDisposed(DisposeEvent event) {
		if (fStatusTextFont != null && 
		        !fStatusTextFont.isDisposed())
			fStatusTextFont.dispose();
		fStatusTextFont= null;

		if (fStatusTextForegroundColor != null && 
		        !fStatusTextForegroundColor.isDisposed())
			fStatusTextForegroundColor.dispose();
		fStatusTextForegroundColor= null;

		fTextFont= null;
		fShell= null;
		fText= null;
	}

	/**
	 * {@inheritDoc}
	 */
	public final void dispose() {
        docProvider.disconnect(ei);
        ei = null;
        
		if (!fIsSystemBackgroundColor)
			fBackgroundColor.dispose();
		if (fShell != null && !fShell.isDisposed())
			fShell.dispose();
		else
			widgetDisposed(null);
	}

	/*
	 * @see IInformationControl#setSize(int, int)
	 */
	public void setSize(int width, int height) {
		fShell.setSize(width, height);
	}

	/*
	 * @see IInformationControl#setLocation(Point)
	 */
	public void setLocation(Point location) {
		fShell.setLocation(location);
	}

	/*
	 * @see IInformationControl#setSizeConstraints(int, int)
	 */
	public void setSizeConstraints(int maxWidth, int maxHeight) {
		fMaxWidth= maxWidth;
		fMaxHeight= maxHeight;
	}

	/*
	 * @see IInformationControl#computeSizeHint()
	 */
	public Point computeSizeHint() {
		// compute the preferred size
		int x= SWT.DEFAULT;
		int y= SWT.DEFAULT;
		Point size= fShell.computeSize(x, y);
		if (size.x > fMaxWidth)
			x= fMaxWidth;
		if (size.y > fMaxHeight)
			y= fMaxHeight;

		// recompute using the constraints if the preferred size is larger than the constraints
		if (x != SWT.DEFAULT || y != SWT.DEFAULT)
			size= fShell.computeSize(x, y, false);

		return size;
	}

	/*
	 * @see IInformationControl#addDisposeListener(DisposeListener)
	 */
	public void addDisposeListener(DisposeListener listener) {
		fShell.addDisposeListener(listener);
	}

	/*
	 * @see IInformationControl#removeDisposeListener(DisposeListener)
	 */
	public void removeDisposeListener(DisposeListener listener) {
		fShell.removeDisposeListener(listener);
	}

	/*
	 * @see IInformationControl#setForegroundColor(Color)
	 */
	public void setForegroundColor(Color foreground) {
		fText.setForeground(foreground);
	}

	/*
	 * @see IInformationControl#setBackgroundColor(Color)
	 */
	public void setBackgroundColor(Color background) {
		fText.setBackground(background);
	}

	/*
	 * @see IInformationControl#isFocusControl()
	 */
	public boolean isFocusControl() {
		return fShell.getDisplay().getActiveShell() == fShell;
	}

	/*
	 * @see IInformationControl#setFocus()
	 */
	public void setFocus() {
		fShell.forceFocus();
		fText.setFocus();
	}

	/*
	 * @see IInformationControl#addFocusListener(FocusListener)
	 */
	public void addFocusListener(FocusListener listener) {
		fText.addFocusListener(listener);
	}

	/*
	 * @see IInformationControl#removeFocusListener(FocusListener)
	 */
	public void removeFocusListener(FocusListener listener) {
		fText.removeFocusListener(listener);
	}

	/*
	 * @see IInformationControlExtension#hasContents()
	 */
	public boolean hasContents() {
		return fText.getCharCount() > 0;
	}

	protected ISourceViewer getViewer()  {
		return fViewer;
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension3#computeTrim()
	 * @since 3.4
	 */
	public Rectangle computeTrim() {
		Rectangle trim= fShell.computeTrim(0, 0, 0, 0);
		addInternalTrim(trim);
		return trim;
	}

	/**
	 * Adds the internal trimmings to the given trim of the shell.
	 *
	 * @param trim the shell's trim, will be updated
	 * @since 3.4
	 */
	private void addInternalTrim(Rectangle trim) {
		Rectangle textTrim= fText.computeTrim(0, 0, 0, 0);
		trim.x+= textTrim.x;
		trim.y+= textTrim.y;
		trim.width+= textTrim.width;
		trim.height+= textTrim.height;

		if (fStatusField != null) {
//			trim.height+= fSeparator.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
			trim.height+= fStatusField.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
			trim.height+= 1; // verticalSpacing
		}
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension3#getBounds()
	 * @since 3.4
	 */
	public Rectangle getBounds() {
		return fShell.getBounds();
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension3#restoresLocation()
	 * @since 3.4
	 */
	public boolean restoresLocation() {
		return false;
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension3#restoresSize()
	 * @since 3.4
	 */
	public boolean restoresSize() {
		return false;
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension5#getInformationPresenterControlCreator()
	 * @since 3.4
	 */
	public IInformationControlCreator getInformationPresenterControlCreator() {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
			    ToolBarManager tbm = new ToolBarManager(SWT.FLAT);
				SourceViewerInformationControl control = 
				        new SourceViewerInformationControl(editor, 
				                parent, true, fOrientation, tbm);
				tbm.add(new Action() {
				    {
				        setText("Open Declaration");
			            setToolTipText("Open Declaration");
			            setLocalImageDescriptors(this, "goto_input.gif");
				    }
				    @Override
			        public void run() {
				        Navigation.gotoNode(referencedNode, editor);
				    }
                });
				tbm.update(true);
                return control;
			}
		};
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension5#containsControl(org.eclipse.swt.widgets.Control)
	 * @since 3.4
	 */
	public boolean containsControl(Control control) {
		do {
			if (control == fShell)
				return true;
			if (control instanceof Shell)
				return false;
			control= control.getParent();
		} while (control != null);
		return false;
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension5#isVisible()
	 * @since 3.4
	 */
	public boolean isVisible() {
		return fShell != null && 
		        !fShell.isDisposed() && 
		        fShell.isVisible();
	}

	/*
	 * @see org.eclipse.jface.text.IInformationControlExtension5#computeSizeConstraints(int, int)
	 */
	public Point computeSizeConstraints(int widthInChars, int heightInChars) {
		GC gc= new GC(fText);
		gc.setFont(fTextFont);
		int width= gc.getFontMetrics().getAverageCharWidth();
		int height= fText.getLineHeight(); //https://bugs.eclipse.org/bugs/show_bug.cgi?id=377109
		gc.dispose();

		return new Point(widthInChars * width, heightInChars * height);
	}
}
