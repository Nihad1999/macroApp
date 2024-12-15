import tkinter as tk

from tkinter import  messagebox, Listbox, Toplevel
import json
import os
import time
import threading
import atexit

from pynput.keyboard import Controller as KeyboardController
from pynput.mouse import Controller as MouseController, Button
import keyboard as Kb
import mouse as Ms
from ahk import AHK
import subprocess

import win32api
import win32con
import time
from win32con import *

ahk = AHK()
SIGNAL_FILE = "click_signal.txt"





class MacroApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Macro App")
        self.root.geometry("300x300")
        self.root.resizable(False, False)
        self.root.attributes('-topmost', 1)  # Set always on top
        self.listener = None  # Global key listener reference

        # Register cleanup functions
        atexit.register(self.cleanup)
        self.root.protocol("WM_DELETE_WINDOW", self.on_closing)

        self.Mouse_Click_Keeper = None # Chose which clcik listener will work

        self.Mouse_TR_Listener = None

        self.loopCount = 0


        self.stored_key = None  # Variable to store the key
        self.keyboard_controller = KeyboardController()  # Add keyboard controller

        self.is_processing = False  # Add this flag

        # Activation status label
        self.status_label = tk.Label(self.root, text="Status: Deactivated", fg="red")
        self.status_label.pack()

        # Create a frame to hold the Listbox and scrollbar
        listbox_frame = tk.Frame(self.root)
        listbox_frame.pack(fill=tk.X, expand=True)

        # Vertical scrollbar
        scrollbar = tk.Scrollbar(listbox_frame, orient=tk.VERTICAL)

        # Listbox with horizontal orientation
        self.macro_listbox = Listbox(listbox_frame, selectmode=tk.SINGLE, height=5, width=50,
                                      xscrollcommand=scrollbar.set)
        self.macro_listbox.pack(side=tk.TOP, fill=tk.X, expand=True)

        #Get Macro information
        self.selectedTriger = None
        self.selectedTriger_index = None
        self.selectedTriger_macro = None
        self.selected_trigger_key = None
        self.selected_trigger_type = None


        # Configure the scrollbar
        scrollbar.config(command=self.macro_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        # Add/Edit, Activate/Deactivate, Delete Buttons
        tk.Button(root, text="Add/Edit", command=self.open_add_edit_window).pack(pady=5)
        self.activate_button = tk.Button(root, text="Activate", command=self.toggle_macro)
        self.activate_button.pack(pady=5)
        tk.Button(root, text="Delete", command=self.delete_macro).pack(pady=5)
        tk.Button(root, text="Minimize", command=self.minimize_to_icon).pack(pady=5)

        # Load saved macros
        self.load_macros()

        # Bind F1 to activate/deactivate macro
        self.root.bind('<F1>', lambda event: self.toggle_macro())
        #self.root.bind_all('<KeyPress>', self.handle_key_press)  # Bind key press event
        self.macro_listbox.bind('<Button-1>', self.toggle_selection)  # Bind click event to toggle selection


        # Track activation status
        self.is_active = False

        # Initialize keyboard and mouse controllers
        self.keyboard = KeyboardController()
        self.mouse = MouseController()

        # Create icon window
        self.icon_window = None
        self.icon_frame = None
        self.dragging = False
        self.offset_x = 0
        self.offset_y = 0

    def load_macros(self):
        # Load macros from file or create an empty list if none exists
        if os.path.exists('macros.json'):
            with open('macros.json', 'r') as file:
                self.macros = json.load(file)
                for macro in self.macros:
                    self.macro_listbox.insert(tk.END, macro['name'])
        else:
            self.macros = []

    def open_add_edit_window(self):
        selected = self.macro_listbox.curselection()
        if selected:
            edit_macro = self.macros[selected[0]]
        else:
            edit_macro = None
        AddEditWindow(self, edit_macro)

    def toggle_macro(self):
        selected = self.macro_listbox.curselection()
        if not selected:
            messagebox.showerror("Error", "Please select a macro to activate/deactivate.")
            return
        selected_index = selected[0]

        if not self.is_active:
            self.is_active = True
            self.status_label.config(text="Status: Activated", fg="green")
            self.activate_button.config(text="Deactivate")  # Change button text

            print("line 134--Selected macro index:", selected_index)
            print("line 135--Selected macro:", self.macros[selected_index])
            #threading.Thread(target=self.start_macro, args=(self.macros[selected_index],)).start()  # Start macro execution in a new thread
            self.selectedTriger = self.macro_listbox.curselection()
            self.selectedTriger_index = self.selectedTriger[0]
            self.selectedTriger_macro = self.macros[self.selectedTriger_index]
            self.selected_trigger_key = self.selectedTriger_macro['trigger_key']
            self.selected_trigger_type = self.selectedTriger_macro['trigger_type']
            print("Trigger type --" + self.selected_trigger_type)

            if not self.selected_trigger_type == 'key' and not self.selected_trigger_type == 'text' and self.selectedTriger_macro.get('ignore_trigger', 'no') == 'no':
                if self.selected_trigger_key == 'Button.left':
                    self.Mouse_Click_Keeper = 'Button.left'
                    def Mouse_Listener_Keeper():
                        Ms.on_click(self.handle_click_press)
                    self.inner_Mouse_listener_keeper = Mouse_Listener_Keeper
                    self.inner_Mouse_listener_keeper()
                else:
                    print("I am right click without block")
                    self.Mouse_Click_Keeper = 'Button.right'
                    def Mouse_Listener_Keeper():
                        Ms.on_right_click(self.handle_click_press)
                    self.inner_Mouse_listener_keeper = Mouse_Listener_Keeper
                    self.inner_Mouse_listener_keeper()
            if not self.selected_trigger_type == 'key' and not self.selected_trigger_type == 'text' and self.selectedTriger_macro.get('ignore_trigger', 'no') == 'yes':
                if self.selected_trigger_key == 'Button.left':
                    self.Mouse_Click_Keeper = 'Button.left'
                    print("Line 155")
                    threading.Thread(target=self.run_ahk_left_script(), daemon=True).start()
                    self.check_for_signal()

                else:
                    print("I am right click")
                    self.Mouse_Click_Keeper = 'Button.right'
                    threading.Thread(target=self.run_ahk_right_script, daemon=True).start()
                    self.check_for_signal()


            if self.selectedTriger_macro.get('ignore_trigger', 'no') == 'yes' and self.selected_trigger_type == 'key' or self.selected_trigger_type == 'text':
                print("I will ignore trigger key")
                Kb.hook(self.check_ignore_Trigger_Key)
                Kb.on_press_key(self.selected_trigger_key, lambda e: None, suppress=True)
                time.sleep(0.1)
            elif self.selected_trigger_type == 'key' or self.selected_trigger_type == 'text':
                Kb.hook(self.handle_key_press)


        else:
            self.is_active = False
            self.status_label.config(text="Status: Deactivated", fg="red")
            self.activate_button.config(text="Activate")  # Change button text
            Kb.unhook_all()
            Ms.unhook_all()
            self.cleanup()

    def kill_ahk_processes(self):
        # Kill all AutoHotkey processes
        subprocess.run(['taskkill', '/F', '/IM', 'AutoHotkey.exe'],
                       stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)



    def cleanup(self):
        ahk.run_script("ExitApp")
        self.kill_ahk_processes()
        if os.path.exists(SIGNAL_FILE):
            os.remove(SIGNAL_FILE)

    def on_closing(self):
        self.cleanup()
        self.root.destroy()



    def check_for_signal(self):
        if self.is_active and os.path.exists(SIGNAL_FILE):
            os.remove(SIGNAL_FILE)
            self.handle_click_press()
        if self.is_active:
            root.after(10, self.check_for_signal)

    def run_ahk_right_script(self):
        ahk.run_script(f"""
        #Persistent
        RButton::
        FileAppend,, {SIGNAL_FILE}
        return
        """)


    def run_ahk_left_script(self):
        ahk.run_script(f"""
        #Persistent
        LButton::
        FileAppend,, {SIGNAL_FILE}
        return
        """)


    def start_macro(self, macro): #Selected one

        print("line 171-- I am in start )")
        #self.handle_key_press(macro)  # Execute the macro continuously if activated
        #self.check_ignore_Trigger_Key()

    def delete_macro(self):
        selected = self.macro_listbox.curselection()
        if selected:
            macro_name = self.macro_listbox.get(selected[0])
            confirm = messagebox.askyesno("Delete", f"Are you sure you want to delete '{macro_name}'?")
            if confirm:
                del self.macros[selected[0]]
                self.save_macros()
                self.macro_listbox.delete(selected[0])

    def save_macros(self):
        with open('macros.json', 'w') as file:
            json.dump(self.macros, file)

    def check_ignore_Trigger_Key(self, event):
        self.is_processing = False
        #self.loopCount = 0
        print("Line 190 -- I am check_ignore_Trigger_Key ---")

        try:
            if event.name == self.selected_trigger_key:
                print("Line 200 -- check try--" + "Event name  " + event.name)
                self.handle_key_press(event)

                print("Line 258 Event key -- " + str(event.name))

            else:
                print("Line 257 Listener is running-->" + str(self.global_listener.is_alive()))
                print("Line 258 Listener is running-->" + str(self.global_listener.running))

                print("Line 268 Event key -- " + str(event))



        except Exception as e:

            print(f"Error handling key event: {e}")

             # In case of error, allow default action


    def handle_key_press(self, event):
        if self.is_processing:  # Check if we're already processing a key
            return

        if not self.is_active:
            return

        try:
            if isinstance(event, str) or str(event):
                stored_key_Handle = str(event.name)

                if self.selected_trigger_key == stored_key_Handle:
                    threading.Thread(target=self.execute_macro).start()
                else:
                    print("Line 260-- "+self.selected_trigger_key + "  input key  " + stored_key_Handle)

        except:
            pass

    def handle_click_press(self):

        if not self.is_active:
            return

        try:

            threading.Thread(target=self.execute_macro).start()


        except:
            pass


    def check_ignore_Mouse_Click(self, x, y, button, pressed):
        if pressed and button == Button.right:
            self.handle_click_press()
            return False


    def execute_macro(self):
        print("Line 277-- i am in execute macro")
        selected = self.macro_listbox.curselection()
        selected_index = selected[0]
        macro = self.macros[selected_index]

        is_continuous = macro.get('mode', '') == 'continuous'
        actions = macro['actions'].split(',')

        if is_continuous and self.is_active:
            print("Line 285-- i am in loop")
            while self.is_active:
                self._execute_actions_game(actions)
        else:
            print("Line 289-- i am here")
            self._execute_actions_game(actions)


    def _execute_actions(self, actions):
        if not self.selectedTriger_macro.get('ignore_trigger', 'no') == 'yes':
            Kb.unhook_all()
            Ms.unhook_all()
        print("Line 296--  now i am here")

        if self.selectedTriger_macro['trigger_type'] == 'text':
            print("I am text macro ) ")
            text_content = self.selectedTriger_macro.get('text_content', '')
            Kb.write(text_content)
        else:
            for action in actions:
                action = action.strip()
                if action.startswith("delay_before"):
                    delay_time = int(action.split('_')[1]) / 1000  # Convert to seconds
                    time.sleep(delay_time)
                elif action.startswith("k:"):
                    key = action.split(':')[1].strip("'")  # Get the key from the action
                    print("Line 337-- " + key)
                    Kb.press_and_release(str(key))  # Release the key

                elif action.startswith("click:"):
                    click_type = action.split(':')[1].strip()  # Get click type
                    if click_type == "left":
                        self.mouse.click(Button.left)  # Simulate left click
                    elif click_type == "right":
                        self.mouse.click(Button.right)  # Simulate right click
                elif action.startswith("delay_after"):
                    delay_time = int(action.split('_')[1]) / 1000  # Convert to seconds
                    time.sleep(delay_time)
        if not self.selectedTriger_macro.get('ignore_trigger', 'no') == 'yes':
            time.sleep(0.1)
            Kb.hook(self.handle_key_press)
            self.inner_Mouse_listener_keeper()

    def _execute_actions_game(self, actions):
        if not self.selectedTriger_macro.get('ignore_trigger', 'no') == 'yes':
            Kb.unhook_all()
            Ms.unhook_all()
        print("Line 296--  now i am here")

        if self.selectedTriger_macro['trigger_type'] == 'text':
            print("I am text macro ) ")
            text_content = self.selectedTriger_macro.get('text_content', '')
            Kb.write(text_content)
        else:
            for action in actions:
                action = action.strip()
                if action.startswith("delay_before"):
                    delay_time = int(action.split('_')[1]) / 1000  # Convert to seconds
                    time.sleep(delay_time)
                elif action.startswith("k:"):
                    key = action.split(':')[1].strip("'")  # Get the key from the action
                    vk_code = self._get_virtual_key_code(key)
                    print("Line 337-- " + key)
                    win32api.keybd_event(vk_code, 0, 0, 0)  # Press key
                    time.sleep(0.1)
                    win32api.keybd_event(vk_code, 0, win32con.KEYEVENTF_KEYUP, 0)  # Release key  # Release the key

                elif action.startswith("click:"):
                    click_type = action.split(':')[1].strip()  # Get click type
                    if click_type == "left":
                        self.mouse.click(Button.left)  # Simulate left click
                    elif click_type == "right":
                        self.mouse.click(Button.right)  # Simulate right click
                elif action.startswith("delay_after"):
                    delay_time = int(action.split('_')[1]) / 1000  # Convert to seconds
                    time.sleep(delay_time)
        if not self.selectedTriger_macro.get('ignore_trigger', 'no') == 'yes':
            time.sleep(0.1)
            Kb.hook(self.handle_key_press)
            self.inner_Mouse_listener_keeper()




    def _get_virtual_key_code(self, key):
        # Virtual key code mapping
        key_map = {
            'a': 0x41, 'b': 0x42, 'c': 0x43, 'd': 0x44, 'e': 0x45,
            'f': 0x46, 'g': 0x47, 'h': 0x48, 'i': 0x49, 'j': 0x4A,
            'k': 0x4B, 'l': 0x4C, 'm': 0x4D, 'n': 0x4E, 'o': 0x4F,
            'p': 0x50, 'q': 0x51, 'r': 0x52, 's': 0x53, 't': 0x54,
            'u': 0x55, 'v': 0x56, 'w': 0x57, 'x': 0x58, 'y': 0x59,
            'z': 0x5A, '1': 0x31, '2': 0x32, '3': 0x33, '4': 0x34,
            '5': 0x35, '6': 0x36, '7': 0x37, '8': 0x38, '9': 0x39,
            '0': 0x30, 'enter': 0x0D, 'space': 0x20, 'tab': 0x09,
            'esc': 0x1B, 'backspace': 0x08
        }
        return key_map.get(key.lower(), 0)





    def minimize_to_icon(self):
        self.icon_window = tk.Toplevel(self.root)
        self.icon_window.title("Macro App - Minimized")
        self.icon_window.geometry("50x50")  # Small size for the icon window
        self.icon_window.overrideredirect(True)  # Remove window decorations
        self.icon_window.configure(bg="lightgray")
        self.icon_window.bind("<Double-Button-1>", self.restore_from_icon)  # Restore on double click
        self.icon_window.bind("<ButtonPress-1>", self.start_drag)  # Start drag
        self.icon_window.bind("<B1-Motion>", self.drag)  # Perform drag
        self.icon_window.attributes('-topmost', 1)  # Set always on top

        self.icon_frame = tk.Frame(self.icon_window, bg="lightgray")
        self.icon_frame.pack(fill=tk.BOTH, expand=True)
        label = tk.Label(self.icon_frame, text="M", font=("Arial", 24), bg="lightgray")  # 'M' for Macro
        label.pack(fill=tk.BOTH, expand=True)

        self.root.withdraw()  # Hide the main window

    def restore_from_icon(self, event):
        self.icon_window.destroy()  # Destroy the icon window
        self.root.deiconify()  # Show the main window
        self.root.lift()  # Bring the main window to the top

    def start_drag(self, event):
        self.dragging = True
        self.offset_x = event.x
        self.offset_y = event.y

    def drag(self, event):
        if self.dragging:
            x = self.icon_window.winfo_x() - self.offset_x + event.x
            y = self.icon_window.winfo_y() - self.offset_y + event.y
            self.icon_window.geometry(f"+{x}+{y}")  # Move the icon window

    def stop_drag(self, event):
        self.dragging = False

    def toggle_selection(self, event):
        index = self.macro_listbox.nearest(event.y)  # Get the nearest item index to the mouse click
        if index != -1:  # Ensure that we have a valid index
            current_selection = self.macro_listbox.curselection()
            if index in current_selection:  # If it's already selected, deselect it
                self.macro_listbox.selection_clear(index)
            else:  # Otherwise, select it
                self.macro_listbox.selection_set(index)


class AddEditWindow:
    def __init__(self, parent_app, macro=None):
        self.parent_app = parent_app
        self.window = Toplevel(parent_app.root)
        self.window.geometry("300x500")
        self.window.title("Add/Edit Macro")
        self.window.resizable(False, False)
        self.window.attributes('-topmost', 1)  # Set always on top

        # Macro Name
        tk.Label(self.window, text="Macro Name").pack()
        self.name_entry = tk.Entry(self.window)
        self.name_entry.pack(pady=5)

        # Trigger Type Section
        tk.Label(self.window, text="Trigger Type").pack()
        self.trigger_var = tk.StringVar(value="key")
        self.trigger_var.trace('w', self.on_trigger_type_change)  # Add trace
        tk.Radiobutton(self.window, text="Key", variable=self.trigger_var, value="key").pack()
        tk.Radiobutton(self.window, text="Left Click", variable=self.trigger_var, value="left click").pack()
        tk.Radiobutton(self.window, text="Right Click", variable=self.trigger_var, value="right click").pack()
        tk.Radiobutton(self.window, text="Text", variable=self.trigger_var, value="text").pack()

        # Trigger Key
        tk.Label(self.window, text="Trigger Key (if applicable)").pack()
        self.trigger_key_entry = tk.Entry(self.window)
        self.trigger_key_entry.pack(pady=5)

        # Once/Continuous Section
        tk.Label(self.window, text="Trigger Mode").pack()
        self.mode_var = tk.StringVar(value="once")
        tk.Radiobutton(self.window, text="Once", variable=self.mode_var, value="once").pack()
        tk.Radiobutton(self.window, text="Continuous", variable=self.mode_var, value="continuous").pack()

        # Ignore Trigger Option
        self.ignore_trigger_var = tk.StringVar(value='no')
        tk.Checkbutton(self.window, text="Ignore Trigger Key Action", variable=self.ignore_trigger_var,
                       onvalue='yes', offvalue='no').pack(pady=5)
        # Actions
        tk.Label(self.window, text="Actions (e.g., delay_before_500, k:'a', click:left)").pack()
        self.actions_entry = tk.Entry(self.window, width=50)
        self.actions_entry.pack(pady=5)

        # Text Content
        self.text_label = tk.Label(self.window, text="Text Content (for text macro)")
        self.text_content = tk.Entry(self.window, width=50)
        self.text_label.pack(pady=5)
        self.text_content.pack(pady=5)


        # Save/Cancel Buttons
        tk.Button(self.window, text="Save", command=self.save_macro).pack(pady=5)
        tk.Button(self.window, text="Cancel", command=self.window.destroy).pack(pady=5)

        # Populate fields if editing an existing macro
        if macro:
            self.name_entry.insert(0, macro['name'])
            self.trigger_var.set(macro['trigger_type'])
            self.trigger_key_entry.insert(0, macro['trigger_key'])
            self.actions_entry.insert(0, macro['actions'])
            self.ignore_trigger_var.set(macro.get('ignore_trigger', 'no'))
            self.text_content.insert(0, macro['text_content'])


    def on_trigger_type_change(self, *args):
        trigger_type = self.trigger_var.get()
        if trigger_type == "left click":
            value = "Button.left"
            self.trigger_key_entry.config(state='normal')  # Temporarily enable to set value
            self.trigger_key_entry.delete(0, tk.END)
            self.trigger_key_entry.insert(0, value)
            self.trigger_key_entry.config(state='disabled')
            self.actions_entry.config(state='normal')
            self.actions_entry.delete(0, tk.END)
        elif trigger_type == "right click":
            value = "Button.right"
            self.trigger_key_entry.config(state='normal')  # Temporarily enable to set value
            self.trigger_key_entry.delete(0, tk.END)
            self.trigger_key_entry.insert(0, value)
            self.trigger_key_entry.config(state='disabled')
            self.actions_entry.config(state='normal')
            self.actions_entry.delete(0, tk.END)
            #self.window.after(1000, lambda: print(f"Entry value: {self.trigger_key_entry.get()}"))
        elif trigger_type == "text":
            value ="Text"
            self.text_content.config(state='normal')
            self.trigger_key_entry.config(state='normal')
            self.trigger_key_entry.delete(0, tk.END)
            self.actions_entry.delete(0, tk.END)
            self.actions_entry.insert(0, value)
            self.actions_entry.config(state='disabled')


        else:
            self.trigger_key_entry.config(state='normal')
            self.trigger_key_entry.delete(0, tk.END)
            self.actions_entry.config(state='normal')
            self.actions_entry.delete(0, tk.END)
            self.text_content.delete(0, tk.END)
            self.text_content.config(state='disabled')


    def save_macro(self):
        name = self.name_entry.get().strip()
        trigger_type = self.trigger_var.get()
        trigger_key = self.trigger_key_entry.get().strip()
        actions = self.actions_entry.get().strip()
        ignore_trigger = self.ignore_trigger_var.get()
        mode = self.mode_var.get()
        text_content = self.text_content.get().strip()
        print(f"Trigger type selected: {trigger_type}")
        print(f"Trigger key value: {trigger_key}")

        # Modified validation
        if not name or not actions and trigger_type != 'text'  :
            messagebox.showerror("Error", "Please fill all required fields.")
            return

        # For key trigger type, ensure trigger key is provided
        if trigger_type == "key" and not trigger_key:
            messagebox.showerror("Error", "Please specify a trigger key.")
            return

        macro_data = {
            'name': name,
            'trigger_type': trigger_type,
            'trigger_key': trigger_key,
            'actions': actions,
            "mode": mode,
            'ignore_trigger': ignore_trigger,
            'text_content': text_content  # Add this line
        }

        # Update the macro list and save to file
        if self.parent_app.macro_listbox.curselection():
            index = self.parent_app.macro_listbox.curselection()[0]
            self.parent_app.macros[index] = macro_data  # Update existing macro
            self.parent_app.macro_listbox.delete(index)  # Remove the old entry
            self.parent_app.macro_listbox.insert(index, name)  # Insert updated name
        else:
            self.parent_app.macros.append(macro_data)  # Add new macro
            self.parent_app.macro_listbox.insert(tk.END, name)  # Add new entry to Listbox

        self.parent_app.save_macros()  # Save all macros to file
        self.window.destroy()  # Close the add/edit window


if __name__ == "__main__":
    root = tk.Tk()
    app = MacroApp(root)

    root.mainloop()
