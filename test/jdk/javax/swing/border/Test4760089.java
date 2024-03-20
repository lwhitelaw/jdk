/*
 * Copyright (c) 2010, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

/*
 * @test
 * @bug 4760089
 * @summary Tests that titled border do not paint inner titled border over its caption
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual Test4760089
 */

public class Test4760089 {
    public static void main(String[] args) throws Exception {
        String testInstructions = """
                When test starts, a panel with a compound titled border is seen.
                If one of its titles is overstruck with the border's
                line then test fails. Otherwise test passes.""";

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(testInstructions)
                .rows(4)
                .columns(35)
                .splitUI(Test4760089::init)
                .build()
                .awaitAndCheck();
    }

    public static JComponent init() {
        Border border = new EtchedBorder();
        border = new TitledBorder(border, "LEFT", TitledBorder.LEFT, TitledBorder.TOP);
        border = new TitledBorder(border, "RIGHT", TitledBorder.RIGHT, TitledBorder.TOP);

        JPanel panel = new JPanel();
        panel.setBorder(border);
        panel.setPreferredSize(new Dimension(200, 100));
        Box main = Box.createVerticalBox();
        main.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        main.add(panel);
        return main;
    }
}
